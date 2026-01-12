package com.ciro.jreactive;

import com.ciro.jreactive.smart.*;
import com.ciro.jreactive.spi.JrxSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class JrxProtocolHandler {

    private static final Logger log = LoggerFactory.getLogger(JrxProtocolHandler.class);

    private final Map<String, ReactiveVar<?>> bindings;
    private final Set<JrxSession> sessions = ConcurrentHashMap.newKeySet();
    
    private final ObjectMapper mapper;
    private final ScheduledExecutorService scheduler;

    /* --- Config --- */
    private final boolean backpressureEnabled;
    private final int     maxQueue;
    private final int     flushIntervalMs;

    /* --- Back-pressure structures --- */
    private final ConcurrentLinkedQueue<Event> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queueSize = new AtomicInteger(0);
    private final AtomicBoolean flushScheduled = new AtomicBoolean(false);
    
    private final List<Runnable> disposables = new ArrayList<>();

    private record Event(String k, Object v) {}
    private record DeltaPacket(String type, List<?> changes) {}

    public JrxProtocolHandler(ViewNode root,
                              ObjectMapper mapper,
                              ScheduledExecutorService scheduler,
                              boolean backpressureEnabled,
                              int maxQueue,
                              int flushIntervalMs) {
        this.mapper = mapper;
        this.scheduler = scheduler;
        this.backpressureEnabled = backpressureEnabled;
        this.maxQueue = maxQueue;
        this.flushIntervalMs = flushIntervalMs;

        this.bindings = collect(root);

        bindings.forEach((k, v) -> {
            attachSmartListener(k, v.get());
            Runnable unsubscribe = v.onChange(val -> {
                attachSmartListener(k, val);
                broadcast(k, val); 
            });
            disposables.add(unsubscribe);
        });
    }
    
    private void attachSmartListener(String key, Object value) {
        if (value instanceof SmartList<?> list) {
            Consumer<SmartList.Change> l = ch -> broadcastDelta(key, "list", ch);
            list.subscribe(l);
            disposables.add(() -> list.unsubscribe(l));
        } 
        else if (value instanceof SmartMap<?,?> map) {
            Consumer<SmartMap.Change> l = ch -> broadcastDelta(key, "map", ch);
            map.subscribe(l);
            disposables.add(() -> map.unsubscribe(l));
        } 
        else if (value instanceof SmartSet<?> set) {
            Consumer<SmartSet.Change> l = ch -> broadcastDelta(key, "set", ch);
            set.subscribe(l);
            disposables.add(() -> set.unsubscribe(l));
        }
    }

    private void broadcastDelta(String key, String type, Object change) {
        DeltaPacket packet = new DeltaPacket(type, List.of(change));
        
        if (backpressureEnabled) {
            enqueue(key, packet);
            scheduleFlushIfNeeded();
        } else {
            // 🔥 FIX: Enviamos el Delta inmediatamente, NO el snapshot entero.
            // Esto hace que el sistema sea ultra-rápido incluso sin batching.
            sendImmediateDelta(key, packet);
        }
    }

    // --- CICLO DE VIDA ---

    public void onOpen(JrxSession s) {
        sessions.add(s);
        for (var e : bindings.entrySet()) {
            if (s.isOpen()) {
                try {
                    s.sendText(jsonSingle(e.getKey(), e.getValue().get()));
                } catch (Exception ex) {
                    log.error("Error sending init state for key: " + e.getKey(), ex);
                }
            }
        }
    }

    public void onClose(JrxSession s) {
        sessions.remove(s);
        if (sessions.isEmpty()) {
            queue.clear();
            queueSize.set(0);
            disposables.forEach(Runnable::run);
            disposables.clear();
        }
    }

    public void onMessage(JrxSession s, String payload) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> m = mapper.readValue(payload, Map.class);
            @SuppressWarnings("unchecked")
            ReactiveVar<Object> rv = (ReactiveVar<Object>) bindings.get(m.get("k"));
            if (rv != null) rv.set(m.get("v"));
        } catch (Exception e) {
            log.error("Error processing message", e);
        }
    }

    // --- LÓGICA DE ENVÍO ---

    private void broadcast(String k, Object v) {
        if (!backpressureEnabled) {
            sendImmediate(k, v);
            return;
        }
        enqueue(k, v);
        scheduleFlushIfNeeded();
    }

    private void sendImmediate(String k, Object v) {
        sendRawJson(k, v, null);
    }
    
    private void sendImmediateDelta(String k, DeltaPacket dp) {
        sendRawJson(k, null, dp);
    }
    
    private void sendRawJson(String k, Object v, DeltaPacket dp) {
        String msg;
        try {
            msg = buildJsonMessage(k, v, dp);
        } catch (Exception e) {
            log.warn("Failed to serialize message", e);
            return;
        }
        sessions.removeIf(sess -> {
            if (!sess.isOpen()) return true;
            try {
                synchronized (sess) { sess.sendText(msg); }
            } catch (Exception ex) {
                return true;
            }
            return false;
        });
    }
    
    private String buildJsonMessage(String k, Object v, DeltaPacket dp) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("k", k);
        
        if (dp != null) {
            payload.put("delta", true);
            payload.put("type", dp.type());
            payload.put("changes", dp.changes());
        } else {
            payload.put("v", v); 
        }
        return mapper.writeValueAsString(payload);
    }

    private void enqueue(String k, Object v) {
        int size = queueSize.incrementAndGet();
        if (size > maxQueue) {
            int toDrop = size - maxQueue;
            while (toDrop-- > 0) {
                if (queue.poll() != null) queueSize.decrementAndGet();
            }
        }
        queue.offer(new Event(k, v));
    }

    private void scheduleFlushIfNeeded() {
        if (flushScheduled.compareAndSet(false, true)) {
            scheduler.schedule(this::flushQueue, flushIntervalMs, TimeUnit.MILLISECONDS);
        }
    }

    private void flushQueue() {
        flushScheduled.set(false);
        if (queue.isEmpty()) return;

        Map<String, Object> lastByKey = new LinkedHashMap<>();
        
        Event e;
        while ((e = queue.poll()) != null) {
            queueSize.decrementAndGet();
            
            Object newValue = e.v();
            String key = e.k();

            if (newValue instanceof DeltaPacket newDp) {
                Object existing = lastByKey.get(key);
                
                if (existing instanceof DeltaPacket oldDp && oldDp.type().equals(newDp.type())) {
                    // Fusión de Deltas (Correcto)
                    List<Object> merged = new ArrayList<>(oldDp.changes());
                    merged.addAll(newDp.changes());
                    lastByKey.put(key, new DeltaPacket(newDp.type(), merged));
                } 
                else if (existing != null && !(existing instanceof DeltaPacket)) {
                    // 🔥 FIX: Si ya hay un Snapshot (objeto completo), IGNORAMOS el Delta.
                    // ¿Por qué? Porque el Snapshot es la referencia viva al objeto.
                    // Al serializarse al final, ¡ya contendrá este cambio!
                } 
                else {
                    // Si no había nada, guardamos el Delta
                    lastByKey.put(key, newDp);
                }
            } else {
                // Si llega un Snapshot nuevo, reemplaza todo lo anterior (Deltas o Snapshots viejos)
                lastByKey.put(key, newValue);
            }
        }

        // ... resto del método de serialización igual ...
        String jsonPayload;
        try {
            // (Tu código de serialización aquí sigue igual)
            List<Map<String, Object>> payload = new ArrayList<>(lastByKey.size());
            for (var entry : lastByKey.entrySet()) {
                String k = entry.getKey();
                Object v = entry.getValue();
                
                Map<String, Object> m = new HashMap<>();
                m.put("k", k);

                if (v instanceof DeltaPacket dp) {
                    m.put("delta", true);
                    m.put("type", dp.type());
                    m.put("changes", dp.changes());
                } else {
                    m.put("v", v);
                }
                payload.add(m);
            }
            jsonPayload = mapper.writeValueAsString(payload);
        } catch (IOException ex) {
            log.error("Error serializing flush queue", ex);
            return;
        }

        sessions.removeIf(sess -> {
            if (!sess.isOpen()) return true;
            try {
                synchronized (sess) { sess.sendText(jsonPayload); }
            } catch (Exception ex) {
                return true; 
            }
            return false;
        });
    }

    private String jsonSingle(String k, Object v) throws IOException {
        return buildJsonMessage(k, v, null);
    }     

    private Map<String, ReactiveVar<?>> collect(ViewNode node) {
        Map<String, ReactiveVar<?>> map = new HashMap<>();
        if (node instanceof ViewLeaf leaf) {
            map.putAll(leaf.bindings());
            return map;
        }
        if (node instanceof ViewComposite comp) {
            for (ViewNode child : comp.children()) {
                map.putAll(collect(child));
            }
        }
        return map;
    }
}