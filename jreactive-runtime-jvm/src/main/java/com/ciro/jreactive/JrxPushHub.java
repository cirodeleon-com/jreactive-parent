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
    private HtmlComponent pageInstance;
    private final transient Runnable persistenceCallback;

    public JrxPushHub(HtmlComponent root, ObjectMapper mapper, int maxBuffer, JrxMessageBroker broker, String sessionId, Runnable persistenceCallback) {
    	this.pageInstance = root;
        this.mapper = mapper;
        this.maxBuffer = Math.max(100, maxBuffer);
        this.broker = broker;
        this.sessionId = sessionId;
        this.persistenceCallback = persistenceCallback;
        // 👇 Esto llenará bindings Y owners
        this.bindings = collect(root); 
        setupListeners();
        
    }
    
    private void setupListeners() {
        bindings.forEach((k, rv) -> {
            Object initial = rv.get();
            updateSmartSubscription(k, initial);

            Runnable unsub = rv.onChange(val -> {
                updateSmartSubscription(k, val);
                
                HtmlComponent owner = owners.get(rv);
                
                if (owner != null && owner.getClass().isAnnotationPresent(com.ciro.jreactive.annotations.Client.class)) {
                    // MODO CSR: Solo delta JSON
                    String localKey = k.contains(".") ? k.substring(k.lastIndexOf('.') + 1) : k;
                    Map<String, Object> delta = Map.of(localKey, val);
                    onDelta(owner.getId(), "json", delta);
                } else {
                    // MODO SSR: Snapshot completo
                    onSnapshot(k, val); 
                }
            });

            disposables.add(unsub);
        });
    }

    // 🔥 3. NUEVO: Capacidad de cambiar la página vigilada en caliente
    public synchronized void rebind(HtmlComponent newPage) {
        if (this.pageInstance == newPage) return; 

        // A. Limpieza de escuchas viejos
        disposables.forEach(Runnable::run);
        disposables.clear();
        
        activeSmartCleanups.values().forEach(Runnable::run);
        activeSmartCleanups.clear();
        
        owners.clear();
        bindings.clear();

        // B. Cambio de referencia
        this.pageInstance = newPage;

        // C. Recolección y reconexión a la nueva instancia
        this.bindings.putAll(collect(newPage));
        setupListeners();
    }
    
    public HtmlComponent getPageInstance() {
        return this.pageInstance;
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

    private Map<String, ReactiveVar<?>> collect(ViewNode n) {
        return collectRecursive(n, this.pageInstance);
    }

    private Map<String, ReactiveVar<?>> collectRecursive(ViewNode n, HtmlComponent rootPage) {
        Map<String, ReactiveVar<?>> m = new HashMap<>();

        if (n instanceof HtmlComponent hc) {
            // A. Calcular prefijo (Namespace)
            String prefix = "";
            
            // Si el componente NO es la página raíz, DEBE usar su ID como prefijo.
            // Esto alinea la llave del WebSocket con la llave del DOM.
            if (hc != rootPage) {
                prefix = hc.getId() + ".";
            }

            // B. Registrar las variables con el prefijo correcto
            Map<String, ReactiveVar<?>> selfBinds = hc.bindings();
            for (Map.Entry<String, ReactiveVar<?>> entry : selfBinds.entrySet()) {
                String key = prefix + entry.getKey(); // "visible" -> "ClientsPage-JModal-0.visible"
                ReactiveVar<?> rv = entry.getValue();
                
                owners.put(rv, hc);
                m.put(key, rv);
            }

            // C. Recursión a los hijos
            for (HtmlComponent child : hc._children()) {
                m.putAll(collectRecursive(child, rootPage));
            }
            return m; 
        }

        if (n instanceof ViewComposite c) {
            c.children().forEach(ch -> m.putAll(collectRecursive(ch, rootPage)));
        }
        
        if (n instanceof ViewLeaf leaf) {
            leaf.bindings().forEach((k, v) -> m.put(k, v)); 
        }
        
        return m;
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
    
 // ... inside JrxPushHub class ...

    @SuppressWarnings("unchecked")
    public void set(String k, Object v) {
        ReactiveVar<Object> rv = (ReactiveVar<Object>) bindings.get(k);
        
        if (rv == null && k.contains(".")) {
            String rootKey = k.split("\\.")[0];
            rv = (ReactiveVar<Object>) bindings.get(rootKey);
       }
        
        if (rv != null) {
            if (Objects.equals(rv.get(), v)) return;
            rv.set(v);
            // 🔥 4. NOTIFICAR PERSISTENCIA (Para que la RAM se entere)
            if (this.persistenceCallback != null) this.persistenceCallback.run();
            return;
        }
        
        if (k.contains(".")) {
            String[] parts = k.split("\\.");
            String rootKey = parts[0]; 
            rv = (ReactiveVar<Object>) bindings.get(rootKey);
            
            if (rv != null && rv.get() != null) {
                try {
                    if (applyPath(rv.get(), parts, 1, v)) {
                        // 📢 5. Confirmación granular al cliente (Evita bloqueos en JS)
                        onSnapshot(k, v); 
                        
                        // 🔥 6. PERSISTENCIA CRÍTICA (Esto quita el F5)
                        if (this.persistenceCallback != null) {
                            this.persistenceCallback.run();
                        }
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }
        }
    }

    // Helper: Returns true if a change occurred
    private boolean applyPath(Object target, String[] parts, int idx, Object value) throws Exception {
        if (target == null) return false;
        String fieldName = parts[idx];
        
        if (fieldName.equals("class") || fieldName.equals("classLoader")) return false;

        boolean rootLevel = (idx == 1); 
        java.lang.reflect.Field f = findField(target.getClass(), fieldName, rootLevel);

        if (f == null) return false;

        if (idx == parts.length - 1) {
            return setField(target, f, value);
        }

        f.setAccessible(true);
        Object child = f.get(target);
        if (child == null) {
            child = f.getType().getDeclaredConstructor().newInstance();
            f.set(target, child);
        }
        return applyPath(child, parts, idx + 1, value);
    }

    // Helper: Sets field and checks equality
    private boolean setField(Object target, java.lang.reflect.Field f, Object rawValue) throws Exception {
        f.setAccessible(true);
        Object typedValue = mapper.convertValue(rawValue, mapper.constructType(f.getGenericType()));
        
        Object current = f.get(target);
        if (Objects.equals(current, typedValue)) return false; // No change = No broadcast
        
        f.set(target, typedValue);
        return true; // Changed
    }

    // ... findField remains the same ...

}