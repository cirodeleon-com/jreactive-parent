package com.ciro.jreactive;

import com.ciro.jreactive.smart.*;
import com.ciro.jreactive.spi.JrxSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

public class JrxProtocolHandler {
    private static final Logger log = LoggerFactory.getLogger(JrxProtocolHandler.class);
    private final Map<String, ReactiveVar<?>> bindings;
    private final Set<JrxSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper mapper;
    private final ScheduledExecutorService scheduler;
    private final boolean backpressureEnabled;
    private final int maxQueue, flushIntervalMs;
    private final ConcurrentLinkedQueue<Event> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queueSize = new AtomicInteger(0);
    private final AtomicBoolean flushScheduled = new AtomicBoolean(false);
    private final List<Runnable> disposables = new ArrayList<>();
    private final Map<String, Runnable> activeSmartCleanups = new ConcurrentHashMap<>();
    private final Map<Class<?>, Map<String, Field>> fieldCache = new ConcurrentHashMap<>();

    private record Event(String k, Object v) {}
    private record DeltaPacket(String type, List<?> changes) {}

    public JrxProtocolHandler(ViewNode root, ObjectMapper m, ScheduledExecutorService s, boolean bp, int mq, int fi) {
        this.mapper = m; this.scheduler = s; this.backpressureEnabled = bp; this.maxQueue = mq; this.flushIntervalMs = fi;
        this.bindings = collect(root);
        bindings.forEach((k, v) -> {
            updateSmartSubscription(k, v.get());
            disposables.add(v.onChange(val -> { updateSmartSubscription(k, val); broadcast(k, val); }));
        });
    }

    private void updateSmartSubscription(String key, Object val) {
        Runnable old = activeSmartCleanups.remove(key);
        if (old != null) old.run();
        Runnable next = null;
        if (val instanceof SmartList<?> l) { Consumer<SmartList.Change> c = ch -> broadcastDelta(key, "list", ch); l.subscribe(c); next = () -> l.unsubscribe(c); }
        else if (val instanceof SmartMap<?,?> m) { Consumer<SmartMap.Change> c = ch -> broadcastDelta(key, "map", ch); m.subscribe(c); next = () -> m.unsubscribe(c); }
        else if (val instanceof SmartSet<?> s) { Consumer<SmartSet.Change> c = ch -> broadcastDelta(key, "set", ch); s.subscribe(c); next = () -> s.unsubscribe(c); }
        if (next != null) activeSmartCleanups.put(key, next);
    }

    public void onMessage(JrxSession s, String payload) {
        try {
            Map m = mapper.readValue(payload, Map.class);
            String k = (String) m.get("k"); Object v = m.get("v");
            ReactiveVar<Object> rv = (ReactiveVar<Object>) bindings.get(k);
            if (rv != null) rv.set(v);
            else if (k.contains(".")) updateDeep(k, v);
        } catch (Exception e) { log.error("Protocol error", e); }
    }

    private void updateDeep(String fk, Object v) {
        String[] p = fk.split("\\.");
        ReactiveVar<Object> root = null; int start = -1;
        for (int i = p.length - 1; i > 0; i--) {
            root = (ReactiveVar<Object>) bindings.get(String.join(".", Arrays.copyOfRange(p, 0, i)));
            if (root != null) { start = i; break; }
        }
        if (root == null) {
            for (var e : bindings.entrySet()) if (e.getKey().endsWith("." + p[0])) { root = (ReactiveVar<Object>) e.getValue(); start = 1; break; }
        }
        if (root == null || root.get() == null) return;
        Object o = root.get();
        try {
            for (int i = start; i < p.length - 1; i++) {
                Field f = getF(o.getClass(), p[i]);
                if (f == null) return;
                o = f.get(o);
            }
            Field f = getF(o.getClass(), p[p.length - 1]);
            if (f != null) {
                f.set(o, mapper.convertValue(v, mapper.constructType(f.getGenericType())));
                root.set(root.get());
            }
        } catch (Exception e) { log.error("Deep update fail: " + fk, e); }
    }

    private Field getF(Class<?> c, String n) {
        return fieldCache.computeIfAbsent(c, x -> new ConcurrentHashMap<>()).computeIfAbsent(n, x -> {
            Class<?> curr = c;
            while (curr != null && curr != Object.class) {
                try { Field f = curr.getDeclaredField(n); f.setAccessible(true); return f; }
                catch (NoSuchFieldException e) { curr = curr.getSuperclass(); }
            }
            return null;
        });
    }

    public void onOpen(JrxSession s) { sessions.add(s); bindings.forEach((k, v) -> { try { s.sendText(jsonS(k, v.get())); } catch (Exception e) {} }); }
    public void onClose(JrxSession s) { sessions.remove(s); if (sessions.isEmpty()) { queue.clear(); activeSmartCleanups.values().forEach(Runnable::run); activeSmartCleanups.clear(); disposables.forEach(Runnable::run); disposables.clear(); } }
    private void broadcast(String k, Object v) { if (!backpressureEnabled) sendI(k, v); else { enq(k, v); sched(); } }
    private void broadcastDelta(String k, String t, Object c) { DeltaPacket p = new DeltaPacket(t, List.of(c)); if (backpressureEnabled) { enq(k, p); sched(); } else sendRaw(k, null, p); }
    private void sendI(String k, Object v) { sendRaw(k, v, null); }
    private void sendRaw(String k, Object v, DeltaPacket dp) {
        try { String m = buildM(k, v, dp); sessions.removeIf(s -> { if (!s.isOpen()) return true; try { synchronized(s){s.sendText(m);} return false; } catch(Exception e){return true;} }); } catch(Exception e){}
    }
    private String buildM(String k, Object v, DeltaPacket dp) throws IOException { Map p = new HashMap(); p.put("k", k); if (dp != null) { p.put("delta", true); p.put("type", dp.type()); p.put("changes", dp.changes()); } else p.put("v", v); return mapper.writeValueAsString(p); }
    private void enq(String k, Object v) { if (queueSize.incrementAndGet() > maxQueue && queue.poll() != null) queueSize.decrementAndGet(); queue.offer(new Event(k, v)); }
    private void sched() { if (flushScheduled.compareAndSet(false, true)) scheduler.schedule(this::flush, flushIntervalMs, TimeUnit.MILLISECONDS); }
    private void flush() {
        flushScheduled.set(false); if (queue.isEmpty()) return;
        Map<String, Object> last = new LinkedHashMap<>(); Event e;
        while ((e = queue.poll()) != null) {
            queueSize.decrementAndGet(); String k = e.k(); Object nv = e.v();
            if (nv instanceof DeltaPacket nDp) {
                Object ex = last.get(k);
                if (ex instanceof DeltaPacket oDp && oDp.type().equals(nDp.type())) { List m = new ArrayList(oDp.changes()); m.addAll(nDp.changes()); last.put(k, new DeltaPacket(nDp.type(), m)); }
                else if (ex == null || ex instanceof DeltaPacket) last.put(k, nDp);
            } else last.put(k, nv);
        }
        try {
            List list = new ArrayList(); last.forEach((k, v) -> { Map m = new HashMap(); m.put("k", k); if (v instanceof DeltaPacket d) { m.put("delta", true); m.put("type", d.type()); m.put("changes", d.changes()); } else m.put("v", v); list.add(m); });
            String pay = mapper.writeValueAsString(list); sessions.removeIf(s -> { if (!s.isOpen()) return true; try { synchronized(s){s.sendText(pay);} return false; } catch(Exception ex){return true;} });
        } catch(Exception ex) {}
    }
    private String jsonS(String k, Object v) throws IOException { return buildM(k, v, null); }
    private Map<String, ReactiveVar<?>> collect(ViewNode n) {
        Map<String, ReactiveVar<?>> m = new HashMap<>();
        if (n instanceof ViewLeaf l) m.putAll(l.bindings());
        else if (n instanceof ViewComposite c) c.children().forEach(ch -> m.putAll(collect(ch)));
        return m;
    }
}