package com.ciro.jreactive;

import com.ciro.jreactive.smart.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

public class JrxPushHub {

    /** Sink genérico (WS/SSE/lo que sea) */
    public interface JrxSink {
        boolean isOpen();
        void send(String json) throws IOException;
        void close();
    }

    /** DTO simple (sin record) para evitar líos de versión */
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

    private final AtomicLong seq = new AtomicLong(0);

    private final int maxBuffer;
    private final ConcurrentLinkedQueue<Map<String,Object>> buffer = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Long> bufferSeq = new ConcurrentLinkedQueue<>();

    private final Set<JrxSink> sinks = ConcurrentHashMap.newKeySet();
    private final List<Runnable> disposables = new ArrayList<>();

    public JrxPushHub(ViewNode root, ObjectMapper mapper, int maxBuffer) {
        this.mapper = mapper;
        this.maxBuffer = Math.max(100, maxBuffer);
        this.bindings = collect(root);

        // Suscribir listeners una sola vez
        bindings.forEach((k, rv) -> {

            // 1) Hook al valor inicial (si ya era Smart*)
            Object initial = rv.get();
            attachDirtyHook(k, initial);

            // 2) Listener normal: cuando el root cambia (snapshot o nueva referencia)
            Runnable unsub = rv.onChange(val -> {
                // re-hook si cambia la referencia (ej: asignas una nueva SmartList)
                attachDirtyHook(k, val);

                // push normal (y aquí encode decide snapshot vs delta)
                onChange(k, val);
            });

            disposables.add(unsub);
        });

    }

    public void close() {
        disposables.forEach(Runnable::run);
        disposables.clear();

        sinks.forEach(s -> {
            try { s.close(); } catch (Exception ignored) {}
        });
        sinks.clear();

        buffer.clear();
        bufferSeq.clear();
    }

    /*
    @SuppressWarnings("unchecked")
    public void set(String k, Object v) {
        ReactiveVar<Object> rv = (ReactiveVar<Object>) bindings.get(k);
        if (rv != null) rv.set(v);
    }
    */
    
    @SuppressWarnings("unchecked")
    public void set(String k, Object v) {
        // 1. Intento directo (Ej: "count", o si el mapa ya tiene la clave completa)
        ReactiveVar<Object> rv = (ReactiveVar<Object>) bindings.get(k);
        if (rv != null) {
            rv.set(v);
            return;
        }

        // 2. Intento anidado (Ej: "form.name" -> buscar "form" y setear "name")
        if (k.contains(".")) {
            // Buscamos la raíz: "form"
            String[] parts = k.split("\\.");
            String rootKey = parts[0]; 
            
            rv = (ReactiveVar<Object>) bindings.get(rootKey);
            
            if (rv != null) {
                Object rootObj = rv.get();
                if (rootObj != null) {
                    try {
                        // Navegamos dentro del objeto para setear el valor
                        applyPath(rootObj, parts, 1, v);
                        
                        // 🔥 MUY IMPORTANTE:
                        // Al hacer set() del objeto raíz, disparas los listeners 
                        // y el sistema se entera de que hubo un cambio.
                        rv.set(rootObj); 
                        
                    } catch (Exception e) {
                        System.err.println("Error setting nested field " + k + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            } else {
                // Debug opcional: avisar si llega algo que no existe
                // System.out.println("Warning: Binding not found for " + k);
            }
        }
    }

    public Batch snapshot() {
        List<Map<String,Object>> out = new ArrayList<>(bindings.size());
        bindings.forEach((k, rv) -> out.add(encode(k, rv.get(), false)));
        return new Batch(seq.get(), out);
    }

    public Batch poll(long since) {
        if (since <= 0) return snapshot();
        
        long oldestAvailable = bufferSeq.isEmpty() ? seq.get() : bufferSeq.peek();
        
        // Si el cliente pide el 50, pero el buffer empieza en el 100...
        // significa que hemos perdido datos intermedios.
        if (since < oldestAvailable - 1) { // -1 por margen de seguridad
            System.out.println("⚠️ Cliente desincronizado (pide " + since + ", min es " + oldestAvailable + "). Forzando Snapshot.");
            return snapshot(); // 🔥 ROBUSTEZ: Forzamos recarga total
        }

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

    /** Suscripción genérica (sirve para SSE o cualquier stream) */
    public void subscribe(JrxSink sink, long since) {
        sinks.add(sink);

        // enviar estado inicial
        try {
            Batch initial = (since <= 0) ? snapshot() : poll(since);
            String json = mapper.writeValueAsString(toEnvelope(initial));
            sink.send(json);
        } catch (Exception e) {
            try { sink.close(); } catch (Exception ignored) {}
            sinks.remove(sink);
        }
    }

    public void unsubscribe(JrxSink sink) {
        sinks.remove(sink);
        try { sink.close(); } catch (Exception ignored) {}
    }
    
    private void attachDirtyHook(String k, Object val) {
        if (val instanceof SmartList<?> sl) {
            sl.onDirty(() -> onChange(k, sl));
        } else if (val instanceof SmartMap<?,?> sm) {
            sm.onDirty(() -> onChange(k, sm));
        } else if (val instanceof SmartSet<?> ss) {
            ss.onDirty(() -> onChange(k, ss));
        }
    }


    // --- internal onChange ---
    private void onChange(String k, Object v) {
        long s = seq.incrementAndGet();
        Map<String,Object> msg = encode(k, v, true);
        
        if (Boolean.TRUE.equals(msg.get("delta"))) {
            System.out.println("[JRX DELTA] " + k + " type=" + msg.get("type") + " seq=" + s);
        } else {
            System.out.println("[JRX SNAP] " + k + " seq=" + s);
        }

        buffer.add(msg);
        bufferSeq.add(s);

        while (buffer.size() > maxBuffer) {
            buffer.poll();
            bufferSeq.poll();
        }

        String json;
        try {
            json = mapper.writeValueAsString(Map.of(
                    "seq", s,
                    "batch", List.of(msg)
            ));
        } catch (Exception ex) {
            return;
        }

        sinks.removeIf(sink -> {
            if (!sink.isOpen()) return true;
            try {
                sink.send(json);
                return false;
            } catch (Exception e) {
                try { sink.close(); } catch (Exception ignored) {}
                return true;
            }
        });
    }

    private Map<String,Object> encode(String k, Object v, boolean allowDelta) {
        Map<String,Object> payload = new HashMap<>();
        payload.put("k", k);

        if (allowDelta && v instanceof SmartList<?> list && list.isDirty()) {
            payload.put("delta", true);
            payload.put("type", "list");
            payload.put("changes", list.drainChanges());
        } else if (allowDelta && v instanceof SmartMap<?,?> map && map.isDirty()) {
            payload.put("delta", true);
            payload.put("type", "map");
            payload.put("changes", map.drainChanges());
        } else if (allowDelta && v instanceof SmartSet<?> set && set.isDirty()) {
            payload.put("delta", true);
            payload.put("type", "set");
            payload.put("changes", set.drainChanges());
        } else {
            payload.put("v", v);
        }
        return payload;
    }

    private Map<String,Object> toEnvelope(Batch b) {
        Map<String,Object> m = new HashMap<>();
        m.put("seq", b.getSeq());
        m.put("batch", b.getBatch());
        return m;
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
    
 // --- Helpers para inyección de dependencias anidadas ---

    private void applyPath(Object target, String[] parts, int idx, Object value) throws Exception {
        if (target == null) return;
        
        String fieldName = parts[idx];
        
        // Si estamos en el último segmento (ej: "name"), escribimos el valor
        if (idx == parts.length - 1) {
            setField(target, fieldName, value);
            return;
        }
        
        // Si no, bajamos un nivel (ej: "address" en "user.address.city")
        java.lang.reflect.Field f = findField(target.getClass(), fieldName);
        if (f != null) {
            f.setAccessible(true);
            Object child = f.get(target);
            
            // Si el hijo es nulo, intentamos crearlo (si tiene constructor vacío)
            if (child == null) {
                child = f.getType().getDeclaredConstructor().newInstance();
                f.set(target, child);
            }
            applyPath(child, parts, idx + 1, value);
        }
    }

    private void setField(Object target, String fieldName, Object rawValue) throws Exception {
        java.lang.reflect.Field f = findField(target.getClass(), fieldName);
        if (f != null) {
            f.setAccessible(true);
            
            // Usamos Jackson para convertir el tipo (ej: "true" -> boolean, "123" -> int)
            // Esto es vital porque del JSON siempre vienen Strings o tipos primitivos simples.
            Object typedValue = mapper.convertValue(rawValue, mapper.constructType(f.getGenericType()));
            
            f.set(target, typedValue);
        }
    }

    private java.lang.reflect.Field findField(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
    
}
