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
    // 🔥 Usamos la interfaz SPI, no la sesión de Spring
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

        /* listeners para broadcast */
        bindings.forEach((k, v) -> v.onChange(val -> broadcast(k, val)));
    }

    // --- CICLO DE VIDA (Llamado por el adaptador) ---

    public void onOpen(JrxSession s) {
        sessions.add(s);
        // Enviar estado inicial
        for (var e : bindings.entrySet()) {
            System.out.println("JRX-INIT  " + e.getKey() + " = " + e.getValue().get());
            if (s.isOpen()) {
                try {
                    s.sendText(jsonSingle(e.getKey(), e.getValue().get(), false));
                } catch (Exception ex) {
                    log.error("Error sending init state", ex);
                }
            }
        }
    }

    public void onClose(JrxSession s) {
        sessions.remove(s);
        if (sessions.isEmpty()) {
            queue.clear();
            queueSize.set(0);
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
        System.out.println("JRX-BROADCAST " + k + " → " + v);
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
                log.warn("throttled {} events (queue>{})", dropped, maxQueue);
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

                if (v instanceof SmartList<?> list && list.isDirty()) {
                    m.put("delta", true);
                    m.put("type", "list");
                    m.put("changes", new ArrayList<>(list.getChanges()));
                    list.clearChanges();
                } 
                else if (v instanceof SmartMap<?,?> map && map.isDirty()) {
                    m.put("delta", true);
                    m.put("type", "map");
                    m.put("changes", new ArrayList<>(map.getChanges()));
                    map.clearChanges();
                } 
                else if (v instanceof SmartSet<?> set && set.isDirty()) {
                    m.put("delta", true);
                    m.put("type", "set");
                    m.put("changes", new ArrayList<>(set.getChanges()));
                    set.clearChanges();
                } 
                else {
                    m.put("v", v);
                }
                payload.add(m);
            });
            
            jsonPayload = mapper.writeValueAsString(payload);
        } catch (IOException ex) {
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
            payload.put("changes", new ArrayList<>(list.getChanges()));
            System.out.println("🟢 [OPTIMIZADO] Enviando DELTA para: " + k);
            list.clearChanges();
        } 
        else if (allowDelta && v instanceof SmartMap<?,?> map && map.isDirty()) {
            payload.put("delta", true);
            payload.put("type", "map");
            payload.put("changes", new ArrayList<>(map.getChanges()));
            map.clearChanges();
        }
        else if (allowDelta && v instanceof SmartSet<?> set && set.isDirty()) {
            payload.put("delta", true);
            payload.put("type", "set");
            payload.put("changes", new ArrayList<>(set.getChanges()));
            set.clearChanges();
        }
        else {
            payload.put("v", v);
            if (v instanceof Collection) {
                 System.out.println("🔴 [SNAPSHOT] Enviando objeto completo: " + k);
            }
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