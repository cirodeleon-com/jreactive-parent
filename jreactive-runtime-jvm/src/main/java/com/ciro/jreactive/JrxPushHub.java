package com.ciro.jreactive;

import com.ciro.jreactive.smart.*;
import com.ciro.jreactive.spi.JrxMessageBroker;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class JrxPushHub {

    public interface JrxSink {
        boolean isOpen();
        void send(String json) throws IOException;
        void close();
    }

    public static final class Batch {
        private final long seq;
        private final List<Map<String, Object>> batch;

        public Batch(long seq, List<Map<String, Object>> batch) {
            this.seq = seq;
            this.batch = batch;
        }

        public long getSeq() { return seq; }
        public List<Map<String, Object>> getBatch() { return batch; }
    }

    private final ObjectMapper mapper;
    private final Map<String, ReactiveVar<?>> bindings;
    // 👇 1. NUEVO: Mapa de dueños para detectar @Client
    private final Map<ReactiveVar<?>, HtmlComponent> owners = new IdentityHashMap<>();
    
    private final AtomicLong seq = new AtomicLong(0);
    private final int maxBuffer;
    private final ConcurrentLinkedQueue<Map<String,Object>> buffer = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Long> bufferSeq = new ConcurrentLinkedQueue<>();
    private final Set<JrxSink> sinks = ConcurrentHashMap.newKeySet();
    
    private final List<Runnable> disposables = new ArrayList<>();
    private final Map<String, Runnable> activeSmartCleanups = new ConcurrentHashMap<>();
    private final AtomicInteger activeSinks = new AtomicInteger(0);

    private final JrxMessageBroker broker;
    private final String sessionId;

    public JrxPushHub(ViewNode root, ObjectMapper mapper, int maxBuffer, JrxMessageBroker broker, String sessionId) {
        this.mapper = mapper;
        this.maxBuffer = Math.max(100, maxBuffer);
        this.broker = broker;
        this.sessionId = sessionId;
        // 👇 Esto llenará bindings Y owners
        this.bindings = collect(root); 

        bindings.forEach((k, rv) -> {
            Object initial = rv.get();
            updateSmartSubscription(k, initial);

            Runnable unsub = rv.onChange(val -> {
                updateSmartSubscription(k, val);
                
                // 👇 2. LÓGICA INTELEGENTE (@Client vs SSR)
                HtmlComponent owner = owners.get(rv);
                
                if (owner != null && owner.getClass().isAnnotationPresent(com.ciro.jreactive.annotations.Client.class)) {
                    // MODO OPTIMIZADO (CSR): Enviar solo el delta JSON
                    String localKey = k.contains(".") ? k.substring(k.lastIndexOf('.') + 1) : k;
                    Map<String, Object> delta = Map.of(localKey, val);
                    
                    // Enviamos al ID del componente ("CounterLeaf#1"), tipo "json", cambios [delta]
                    onDelta(owner.getId(), "json", delta);
                    
                    // Log opcional para depuración
                    // System.out.println("⚡ [SSE] JSON Delta: " + owner.getId() + " -> " + delta);
                } else {
                    // MODO CLÁSICO (SSR): Enviar snapshot completo de la variable
                    onSnapshot(k, val); 
                }
            });

            disposables.add(unsub);
        });
    }

    private void updateSmartSubscription(String key, Object value) {
        Runnable oldCleanup = activeSmartCleanups.remove(key);
        if (oldCleanup != null) oldCleanup.run();

        Runnable cleanup = null;
        if (value instanceof SmartList<?> list) {
            Consumer<SmartList.Change> l = ch -> onDelta(key, "list", ch);
            list.subscribe(l);
            cleanup = () -> list.unsubscribe(l);
        } else if (value instanceof SmartMap<?,?> map) {
            Consumer<SmartMap.Change> l = ch -> onDelta(key, "map", ch);
            map.subscribe(l);
            cleanup = () -> map.unsubscribe(l);
        } else if (value instanceof SmartSet<?> set) {
            Consumer<SmartSet.Change> l = ch -> onDelta(key, "set", ch);
            set.subscribe(l);
            cleanup = () -> set.unsubscribe(l);
        }

        if (cleanup != null) {
            activeSmartCleanups.put(key, cleanup);
        }
    }

    public void close() {
        disposables.forEach(Runnable::run);
        disposables.clear();
        activeSmartCleanups.values().forEach(Runnable::run);
        activeSmartCleanups.clear();

        sinks.forEach(s -> {
            try { s.close(); } catch (Exception ignored) {}
        });
        sinks.clear();
        buffer.clear();
        bufferSeq.clear();
    }

    @SuppressWarnings("unchecked")
    public void set(String k, Object v) {
        ReactiveVar<Object> rv = (ReactiveVar<Object>) bindings.get(k);
        if (rv != null) {
            rv.set(v);
            return;
        }
        if (k.contains(".")) {
            String[] parts = k.split("\\.");
            String rootKey = parts[0]; 
            rv = (ReactiveVar<Object>) bindings.get(rootKey);
            if (rv != null) {
                Object rootObj = rv.get();
                if (rootObj != null) {
                    try {
                        applyPath(rootObj, parts, 1, v);
                        rv.set(rootObj); 
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public void emitRaw(String json) {
        sinks.forEach(sink -> {
            if (sink.isOpen()) {
                try {
                    sink.send(json);
                } catch (Exception ignored) {}
            }
        });
    }

    public Batch snapshot() {
        List<Map<String,Object>> out = new ArrayList<>(bindings.size());
        bindings.forEach((k, rv) -> out.add(encodeSnapshot(k, rv.get())));
        return new Batch(seq.get(), out);
    }

    public Batch poll(long since) {
        if (since <= 0) return snapshot();
        
        long current = seq.get();
        if (since > current) return snapshot();
        
        long oldestAvailable = bufferSeq.isEmpty() ? seq.get() : bufferSeq.peek();
        if (since < oldestAvailable - 1) return snapshot(); 

        List<Map<String,Object>> out = new ArrayList<>();
        Iterator<Long> itS = bufferSeq.iterator();
        Iterator<Map<String,Object>> itB = buffer.iterator();
        long last = since;

        while (itS.hasNext() && itB.hasNext()) {
            long s = itS.next();
            Map<String,Object> e = itB.next();
            if (s > since) {
                out.add(e);
                last = s;
            }
        }
        return new Batch(last, out);
    }

    public void subscribe(JrxSink sink, long since) {
        activeSinks.incrementAndGet();
        sinks.add(sink);
        try {
            Batch initial = (since <= 0) ? snapshot() : poll(since);
            String json = mapper.writeValueAsString(toEnvelope(initial));
            sink.send(json);
        } catch (Exception e) {
            try { sink.close(); } catch (Exception ignored) {}
            sinks.remove(sink);
            activeSinks.decrementAndGet();
        }
    }

    public void unsubscribe(JrxSink sink) {
        if (sinks.remove(sink)) {
            activeSinks.decrementAndGet();
        }
        try { sink.close(); } catch (Exception ignored) {}
    }

    private void onSnapshot(String k, Object v) {
        pushToBuffer(encodeSnapshot(k, v));
    }

    private void onDelta(String k, String type, Object change) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("k", k);
        msg.put("delta", true);
        msg.put("type", type);
        msg.put("changes", List.of(change));
        pushToBuffer(msg);
    }

    private void pushToBuffer(Map<String, Object> msg) {
        long s = seq.incrementAndGet();
        buffer.add(msg);
        bufferSeq.add(s);

        while (buffer.size() > maxBuffer) {
            buffer.poll();
            bufferSeq.poll();
        }
        broadcastToSinks(msg, s);
    }

    private void broadcastToSinks(Map<String, Object> msg, long s) {
        String json;
        try {
            json = mapper.writeValueAsString(Map.of(
                    "seq", s,
                    "batch", List.of(msg)
            ));
        } catch (Exception ex) {
            return;
        }
        
        emitRaw(json);

        if (broker != null) {
            broker.publish(sessionId, json);
        }
    }

    private Map<String,Object> encodeSnapshot(String k, Object v) {
        Map<String,Object> payload = new HashMap<>();
        payload.put("k", k);
        payload.put("v", v);
        return payload;
    }

    private Map<String,Object> toEnvelope(Batch b) {
        Map<String,Object> m = new HashMap<>();
        m.put("seq", b.getSeq());
        m.put("batch", b.getBatch());
        return m;
    }

 // 👇 ESTE ES EL MÉTODO QUE NECESITAS PARA QUE SSE/POLL VEA LOS HIJOS (RELOJ)
    private Map<String, ReactiveVar<?>> collect(ViewNode n) {
        Map<String, ReactiveVar<?>> m = new HashMap<>();

        // 1. Si es un componente HTML (ViewLeaf)
        if (n instanceof HtmlComponent hc) {
            // A. Registramos sus propias variables
            Map<String, ReactiveVar<?>> selfBinds = hc.bindings();
            selfBinds.values().forEach(rv -> owners.put(rv, hc)); // Registramos dueño
            m.putAll(selfBinds);

            // B. 🔥 CRÍTICO: ¡Entrar recursivamente en los hijos!
            // Sin esto, el Hub no ve el 'clock' del ClockLeaf porque está anidado.
            for (HtmlComponent child : hc._children()) {
                m.putAll(collect(child));
            }
            return m; 
        }

        // 2. Soporte para ViewComposite puros
        if (n instanceof ViewComposite c) {
            c.children().forEach(ch -> m.putAll(collect(ch)));
        }
        
        // 3. Fallback
        if (n instanceof ViewLeaf leaf) {
            m.putAll(leaf.bindings());
        }
        
        return m;
    }

    private void applyPath(Object target, String[] parts, int idx, Object value) throws Exception {
        if (target == null) return;
        String fieldName = parts[idx];
        
        if (fieldName.equals("class") || fieldName.equals("classLoader")) return;

        boolean rootLevel = (idx == 1); // idx=1 es el primer field dentro del root reactivo
        java.lang.reflect.Field f = findField(target.getClass(), fieldName, rootLevel);

        if (f == null) return;

        if (idx == parts.length - 1) {
            setField(target, f, value);
            return;
        }

        f.setAccessible(true);
        Object child = f.get(target);
        if (child == null) {
            child = f.getType().getDeclaredConstructor().newInstance();
            f.set(target, child);
        }
        applyPath(child, parts, idx + 1, value);
    }

    private void setField(Object target, java.lang.reflect.Field f, Object rawValue) throws Exception {
        f.setAccessible(true);
        Object typedValue = mapper.convertValue(rawValue, mapper.constructType(f.getGenericType()));
        f.set(target, typedValue);
    }
    
    private java.lang.reflect.Field findField(Class<?> clazz, String name, boolean rootLevel) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                java.lang.reflect.Field f = current.getDeclaredField(name);

                // Bloqueos duros
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) return null;
                if ("class".equals(name) || "classLoader".equals(name) || name.startsWith("this$")) return null;
                if ("serialVersionUID".equals(name)) return null;

                if (rootLevel) {
                    if (!(f.isAnnotationPresent(State.class) || f.isAnnotationPresent(Bind.class))) {
                        return null;
                    }
                } else {
                    // DTO interno: solo public
                    if (!java.lang.reflect.Modifier.isPublic(f.getModifiers())) return null;
                }

                f.setAccessible(true);
                return f;

            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

}