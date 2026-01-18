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
        ReactiveVar<Object> root = null;
        int start = -1;
        
        for (int i = p.length - 1; i > 0; i--) {
            root = (ReactiveVar<Object>) bindings.get(String.join(".", Arrays.copyOfRange(p, 0, i)));
            if (root != null) { start = i; break; }
        }
        if (root == null) {
            for (var e : bindings.entrySet()) if (e.getKey().endsWith("." + p[0])) { root = (ReactiveVar<Object>) e.getValue(); start = 1; break; }
        }
        
        // Si el root existe, ya es seguro porque buildBindings() solo mete campos @State/@Bind
        if (root == null || root.get() == null) return;
        
        Object o = root.get();
        try {
            for (int i = start; i < p.length - 1; i++) {
                // 🔥 SEGURIDAD: Bloqueamos acceso explícito a metadatos de Java
                if (p[i].equals("class") || p[i].equals("classLoader")) return;

                Field f = getF(o.getClass(), p[i]);
                if (f == null) return;
                o = f.get(o);
                if (o == null) return; 
            }
            
            Field f = getF(o.getClass(), p[p.length - 1]);
            if (f != null) {
                // 🔥 SEGURIDAD: Bloqueamos escritura en campos sensibles
                if (f.getName().equals("class")) return;

                Object incoming = mapper.convertValue(v, mapper.constructType(f.getGenericType()));
                Object current = f.get(o);

                if (current instanceof SmartList && incoming instanceof List<?> list) {
                    f.set(o, new SmartList<>(list));
                } 
                else if (current instanceof SmartSet && incoming instanceof Collection<?> col) {
                    f.set(o, new SmartSet<>(col));
                } 
                else if (current instanceof SmartMap && incoming instanceof Map<?,?> map) {
                    f.set(o, new SmartMap<>(map));
                } 
                else {
                    f.set(o, incoming);
                }
                root.set(root.get());
            }
        } catch (Exception e) { 
            log.error("Deep update fail: " + fk, e); 
        }
    }
    
    private Field getF(Class<?> c, String n) {
        return fieldCache.computeIfAbsent(c, x -> new ConcurrentHashMap<>()).computeIfAbsent(n, x -> {
            Class<?> curr = c;
            while (curr != null && curr != Object.class) {
                try {
                    Field f = curr.getDeclaredField(n);
                    
                    // 🔥 LA CLAVE DE SEGURIDAD:
                    // Solo permitimos campos que el desarrollador marcó como reactivos.
                    // Esto evita que modifiquen campos internos como 'password' o 'isAdmin'.
                    if (f.isAnnotationPresent(com.ciro.jreactive.State.class) || 
                        f.isAnnotationPresent(com.ciro.jreactive.Bind.class)) {
                        
                        f.setAccessible(true);
                        return f;
                    } else {
                        log.warn("🚨 Intento de acceso no autorizado al campo: {} en la clase {}", n, c.getName());
                        return null; 
                    }
                } catch (NoSuchFieldException e) {
                    curr = curr.getSuperclass();
                }
            }
            return null;
        });
    }

 // 🔥 CAMBIO: Ahora aceptamos el Hub y el cursor 'since' opcionales
    public void onOpen(JrxSession s, JrxPushHub hub, long since) {
        sessions.add(s);

        // 1. RECUPERACIÓN DE HISTORIAL (Solo si es una reconexión)
        if (hub != null && since > 0) {
            try {
                // Pedimos los mensajes perdidos al Hub
                var missedBatch = hub.poll(since);
                
                // Si hay mensajes que el cliente se perdió, se los enviamos YA.
                if (!missedBatch.getBatch().isEmpty()) {
                    Map<String, Object> envelope = new HashMap<>();
                    envelope.put("seq", missedBatch.getSeq());
                    envelope.put("batch", missedBatch.getBatch());
                    
                    s.sendText(mapper.writeValueAsString(envelope));
                    log.info("Recovered {} missed messages for session {}", missedBatch.getBatch().size(), s.getId());
                }
            } catch (Exception e) {
                log.warn("Failed to recover history for session " + s.getId(), e);
            }
        }

        // 2. ENVÍO DE ESTADO ACTUAL (Snapshot)
        // El comportamiento estándar de siempre
        bindings.forEach((k, v) -> {
            try {
                s.sendText(jsonS(k, v.get()));
            } catch (Exception e) {
                // ignorar errores de envío inicial
            }
        });
    }
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
        flushScheduled.set(false);
        if (queue.isEmpty()) return;

        Map<String, Object> last = new LinkedHashMap<>();
        Event e;
        
        while ((e = queue.poll()) != null) {
            queueSize.decrementAndGet();
            String k = e.k();
            Object nv = e.v(); // Nuevo valor (puede ser Objeto o DeltaPacket)

            if (nv instanceof DeltaPacket nDp) {
                Object ex = last.get(k); // Valor existente en el buffer

                // Caso A: Ya había un Delta del mismo tipo -> Fusionar cambios
                if (ex instanceof DeltaPacket oDp && oDp.type().equals(nDp.type())) {
                    List m = new ArrayList(oDp.changes());
                    m.addAll(nDp.changes());
                    last.put(k, new DeltaPacket(nDp.type(), m));
                }
                // Caso B: No había nada O lo que había era un Delta de otro tipo (raro) -> Reemplazar
                else if (ex == null || ex instanceof DeltaPacket) {
                    last.put(k, nDp);
                }
                // 🔥 FIX BUG #2: Conflicto Snapshot vs Delta
                // Caso C: 'ex' es un Snapshot (Objeto completo) y 'nv' es un Delta.
                // No podemos aplicar el delta al snapshot serializado fácilmente.
                // ESTRATEGIA: Forzar un nuevo Snapshot con el valor real actual del servidor.
                else {
                    ReactiveVar<?> rv = bindings.get(k);
                    if (rv != null) {
                        last.put(k, rv.get());
                    }
                }
            } else {
                // Si llega un Snapshot (valor completo), siempre reemplaza lo anterior
                last.put(k, nv);
            }
        }

        try {
            List list = new ArrayList();
            last.forEach((k, v) -> {
                Map m = new HashMap();
                m.put("k", k);
                if (v instanceof DeltaPacket d) {
                    m.put("delta", true);
                    m.put("type", d.type());
                    m.put("changes", d.changes());
                } else {
                    m.put("v", v);
                }
                list.add(m);
            });
            
            String pay = mapper.writeValueAsString(list);
            
            sessions.removeIf(s -> {
                if (!s.isOpen()) return true;
                try {
                    synchronized (s) { s.sendText(pay); }
                    return false;
                } catch (Exception ex) { return true; }
            });
        } catch (Exception ex) {
            log.error("Flush failed", ex);
        }
    }
    private String jsonS(String k, Object v) throws IOException { return buildM(k, v, null); }
    private Map<String, ReactiveVar<?>> collect(ViewNode n) {
        Map<String, ReactiveVar<?>> m = new HashMap<>();
        if (n instanceof ViewLeaf l) m.putAll(l.bindings());
        else if (n instanceof ViewComposite c) c.children().forEach(ch -> m.putAll(collect(ch)));
        return m;
    }
}