package com.ciro.jreactive;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.ciro.jreactive.smart.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handler WS con back-pressure opcional.
 */
public class JReactiveSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(JReactiveSocketHandler.class);

    private final Map<String, ReactiveVar<?>> bindings;
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
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

    /* Event wrapper */
    private record Event(String k, Object v) {}

    /** Constructor */
    public JReactiveSocketHandler(ViewNode root,
                                  ObjectMapper mapper,
                                  ScheduledExecutorService scheduler,
                                  WsConfig cfg) {
        this.mapper  = mapper;
        this.scheduler = scheduler;
        this.backpressureEnabled = cfg.isEnabledBackpressure();
        this.maxQueue = cfg.getMaxQueue();
        this.flushIntervalMs = cfg.getFlushIntervalMs();

        /* SIEMPRE recoge recursivamente TODO el árbol */
        this.bindings = collect(root);

        /* listeners para broadcast */
        bindings.forEach((k, v) -> v.onChange(val -> broadcast(k, val)));
    }

    /* 1. registrar sesión */
    @Override
    public void afterConnectionEstablished(WebSocketSession s) throws Exception {
        sessions.add(s);
        for (var e : bindings.entrySet()) {
        	System.out.println("WS‑INIT  " + e.getKey() + " = " + e.getValue().get());
            if (s.isOpen()) {
                s.sendMessage(jsonSingle(e.getKey(), e.getValue().get(),false));
            }
        }
    }

    /* 2. retirar sesión */
    @Override
    public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
        sessions.remove(s);
        if (sessions.isEmpty()) {
            // nadie escuchando → opcional limpiar la cola
            queue.clear();
            queueSize.set(0);
            // flushScheduled se irá a false cuando dispare flushQueue o puedes dejarlo
        }   
    }

    /* 3. cliente -> servidor */
    @Override
    protected void handleTextMessage(WebSocketSession s, TextMessage msg) throws Exception {
        Map<String, String> m = mapper.readValue(msg.getPayload(), Map.class);
        @SuppressWarnings("unchecked")
        ReactiveVar<Object> rv = (ReactiveVar<Object>) bindings.get(m.get("k"));
        if (rv != null) rv.set(m.get("v"));
    }

    /* 4. servidor -> clientes */
    private void broadcast(String k, Object v) {
    	System.out.println("WS‑BROADCAST " + k + " → " + v);
        if (!backpressureEnabled) {
            // modo simple: envía cada cambio inmediatamente
            sendImmediate(k, v);
            return;
        }

        // modo back-pressure
        enqueue(k, v);
        scheduleFlushIfNeeded();
    }

    private void sendImmediate(String k, Object v) {
        TextMessage msg;
        try {
            msg = jsonSingle(k, v, true);
        } catch (Exception e) {
            return;
        }
        sessions.removeIf(sess -> {
            if (!sess.isOpen()) return true;
            try {
                synchronized (sess) { sess.sendMessage(msg); }
            } catch (Exception ex) {
                return true;
            }
            return false;
        });
    }

    private void enqueue(String k, Object v) {
        int size = queueSize.incrementAndGet();
        if (size > maxQueue) {
            // descarta los más antiguos
            int toDrop = size - maxQueue;
            int dropped = 0;
            while (toDrop-- > 0) {
                Event old = queue.poll();
                if (old != null) {
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

        // Coalesce últimos valores por clave
        Map<String, Object> lastByKey = new LinkedHashMap<>();
        Event e;
        while ((e = queue.poll()) != null) {
            lastByKey.put(e.k(), e.v());
            queueSize.decrementAndGet();
        }

        TextMessage msg;
        try {
            // convertimos a lista [{k:..., v:...}, ...]
            List<Map<String, Object>> payload = new ArrayList<>(lastByKey.size());
            /* === En JReactiveSocketHandler.java, dentro de flushQueue() === */

            /* Dentro del forEach en flushQueue */
            lastByKey.forEach((k, v) -> {
                Map<String, Object> m = new HashMap<>();
                m.put("k", k);

                if (v instanceof SmartList<?> list && list.isDirty()) {
                    m.put("delta", true);
                    m.put("type", "list");
                    m.put("changes", new ArrayList<>(list.getChanges())); // 1. Copia los cambios
                    list.clearChanges();                                  // 2. 🔥 ¡LIMPIAR LA BITÁCORA!
                } 
                else if (v instanceof SmartMap<?,?> map && map.isDirty()) {
                    m.put("delta", true);
                    m.put("type", "map");
                    m.put("changes", new ArrayList<>(map.getChanges()));    // 1. Copia
                    map.clearChanges();                                   // 2. 🔥 LIMPIAR
                } 
                else if (v instanceof SmartSet<?> set && set.isDirty()) {
                    m.put("delta", true);
                    m.put("type", "set");
                    m.put("changes", new ArrayList<>(set.getChanges()));  // 1. Copia
                    set.clearChanges();                                   // 2. 🔥 LIMPIAR
                } 
                else {
                    m.put("v", v);
                }
                payload.add(m);
            });
            
            msg = new TextMessage(mapper.writeValueAsString(payload));
        } catch (IOException ex) {
            return;
        }

        sessions.removeIf(sess -> {
            if (!sess.isOpen()) return true;
            try {
                synchronized (sess) {
                    sess.sendMessage(msg);
                }
            } catch (IllegalStateException ise) {
                // TEXT_FULL o buffer lleno: lo ignoramos, se reenviará en el próximo flush
                log.debug("WS buffer full, ignoring until next flush: {}", ise.getMessage());
                return false;
            } catch (IOException ex) {
                return true;
            }
            return false;
        });
    }

 // En JReactiveSocketHandler.java

 // Importamos tus clases Smart


 // Helper para compatibilidad interna si alguien lo llama sin boolean
    private TextMessage jsonSingle(String k, Object v) throws IOException {
        return jsonSingle(k, v, true); 
    }

    // El método real con la lógica protegida
    private TextMessage jsonSingle(String k, Object v, boolean allowDelta) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("k", k);

        // Solo entramos a la lógica Smart si:
        // 1. allowDelta es TRUE (es un broadcast, no una conexión inicial)
        // 2. El objeto es Smart y está sucio
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
            // Fallback: Snapshot completo (para inicio o si no hay cambios smart)
            payload.put("v", v);
            // Opcional: Log solo si es una colección grande para no spamear
            if (v instanceof Collection) {
                 System.out.println("🔴 [SNAPSHOT] Enviando objeto completo: " + k);
            }
        }
        
        return new TextMessage(mapper.writeValueAsString(payload));
    }    
    /* Recoger bindings de todo el árbol */
    private Map<String, ReactiveVar<?>> collect(ViewNode node) {
        Map<String, ReactiveVar<?>> map = new HashMap<>();

        // ⚠️ IMPORTANTE:
        // Si es un ViewLeaf (HtmlComponent incluido), sus bindings()
        // YA traen todo el árbol con nombres con namespace: hello.fruits, reloj.clock...
        if (node instanceof ViewLeaf leaf) {
            map.putAll(leaf.bindings());
            return map;   // ← AQUÍ TERMINA, NO SE SIGUE RECORRIENDO HIJOS
        }

        // Sólo recorremos hijos en composites "puros"
        if (node instanceof ViewComposite comp) {
            for (ViewNode child : comp.children()) {
                map.putAll(collect(child));
            }
        }

        return map;
    }


}
