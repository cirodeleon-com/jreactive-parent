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

/**
 * Lógica del protocolo JReactive (Agnóstica del servidor).
 * Maneja Deltas, Backpressure y Bindings.
 */
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
    
    // Lista de tareas de limpieza (desuscripción de listeners)
    private final List<Runnable> disposables = new ArrayList<>();

    private record Event(String k, Object v) {}

    /** Constructor con tipos primitivos para no depender de WsConfig */
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

        /* Recoge recursivamente TODO el árbol */
        this.bindings = collect(root);

        /* listeners para broadcast con limpieza automática */
        bindings.forEach((k, v) -> {
            // Guardamos el ticket de desuscripción
            Runnable unsubscribe = v.onChange(val -> broadcast(k, val));
            disposables.add(unsubscribe);
        });
    }

    // --- CICLO DE VIDA (Llamado por el adaptador) ---

    public void onOpen(JrxSession s) {
        sessions.add(s);
        // Enviar estado inicial (Snapshot)
        for (var e : bindings.entrySet()) {
            if (s.isOpen()) {
                try {
                    // false = handshake inicial siempre es snapshot completo
                    s.sendText(jsonSingle(e.getKey(), e.getValue().get(), false));
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
            
            // 🔥 CRÍTICO: Desconectar los cables al salir para evitar fugas de memoria
            if (log.isDebugEnabled()) {
                log.debug("ProtocolHandler closed. Cleaning up {} listeners.", disposables.size());
            }
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

    // --- LÓGICA DE BROADCAST & DELTAS ---

    private void broadcast(String k, Object v) {
        if (!backpressureEnabled) {
            sendImmediate(k, v);
            return;
        }
        enqueue(k, v);
        scheduleFlushIfNeeded();
    }

    private void sendImmediate(String k, Object v) {
        String msg;
        try {
            msg = jsonSingle(k, v, true);
        } catch (Exception e) {
            log.warn("Failed to serialize immediate message for key: " + k, e);
            return;
        }
        sessions.removeIf(sess -> {
            if (!sess.isOpen()) return true;
            try {
                // Sincronizamos por si el adaptador subyacente no es thread-safe
                synchronized (sess) { sess.sendText(msg); }
            } catch (Exception ex) {
                return true;
            }
            return false;
        });
    }

    private void enqueue(String k, Object v) {
        int size = queueSize.incrementAndGet();
        if (size > maxQueue) {
            int toDrop = size - maxQueue;
            int dropped = 0;
            while (toDrop-- > 0) {
                if (queue.poll() != null) {
                    queueSize.decrementAndGet();
                    dropped++;
                }
            }
            if (dropped > 0) {
                log.warn("Throttled {} events (queue size > {})", dropped, maxQueue);
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
            lastByKey.put(e.k(), e.v());
            queueSize.decrementAndGet();
        }

        String jsonPayload;
        try {
            List<Map<String, Object>> payload = new ArrayList<>(lastByKey.size());
            
            lastByKey.forEach((k, v) -> {
                Map<String, Object> m = new HashMap<>();
                m.put("k", k);

                // Detección de cambios (Deltas)
                if (v instanceof SmartList<?> list && list.isDirty()) {
                    m.put("delta", true);
                    m.put("type", "list");
                    m.put("changes", list.drainChanges());
                    // Limpiamos los cambios AQUI, después de empaquetarlos
                    //list.clearChanges();
                } 
                else if (v instanceof SmartMap<?,?> map && map.isDirty()) {
                    m.put("delta", true);
                    m.put("type", "map");
                    m.put("changes", map.drainChanges());
                    //map.clearChanges();
                } 
                else if (v instanceof SmartSet<?> set && set.isDirty()) {
                    m.put("delta", true);
                    m.put("type", "set");
                    m.put("changes", set.drainChanges());
                    //set.clearChanges();
                } 
                else {
                    m.put("v", v);
                }
                payload.add(m);
            });
            
            jsonPayload = mapper.writeValueAsString(payload);
          
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex2) {
            // 🛡️ ESCUDO CRÍTICO: Si Jackson falla (ej. Referencia Circular), no matamos el hilo.
            log.error("🔥 Error CRÍTICO de serialización JSON en broadcast. Posible referencia circular.", ex2);
            return; // Abortamos este flush para proteger el socket
        } catch (IOException ex) {
            log.error("Error serializing flush queue", ex);
            return;
        }

        sessions.removeIf(sess -> {
            if (!sess.isOpen()) return true;
            try {
                synchronized (sess) { sess.sendText(jsonPayload); }
            } catch (Exception ex) {
                // buffer full or error
                return true; 
            }
            return false;
        });
    }

    // --- HELPERS (JSON & Collect) ---

    private String jsonSingle(String k, Object v, boolean allowDelta) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("k", k);

        if (allowDelta && v instanceof SmartList<?> list && list.isDirty()) {
            payload.put("delta", true);
            payload.put("type", "list");
            payload.put("changes", list.drainChanges());
            //list.clearChanges();
        } 
        else if (allowDelta && v instanceof SmartMap<?,?> map && map.isDirty()) {
            payload.put("delta", true);
            payload.put("type", "map");
            payload.put("changes", map.drainChanges());
            //map.clearChanges();
        }
        else if (allowDelta && v instanceof SmartSet<?> set && set.isDirty()) {
            payload.put("delta", true);
            payload.put("type", "set");
            payload.put("changes", set.drainChanges());
            //set.clearChanges();
        }
        else {
            payload.put("v", v);
        }
        return mapper.writeValueAsString(payload);
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