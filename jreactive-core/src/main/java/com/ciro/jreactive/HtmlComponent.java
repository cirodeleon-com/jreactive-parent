package com.ciro.jreactive;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Collection;
import java.util.Objects;
import com.ciro.jreactive.smart.SmartList;
import com.ciro.jreactive.smart.SmartSet;
import com.ciro.jreactive.smart.SmartMap;

public abstract class HtmlComponent extends ViewLeaf implements java.io.Serializable {
	
	private volatile long _version = 0;

    private Map<String, ReactiveVar<?>> map;
    private ComponentEngine.Rendered cached;
    private final List<HtmlComponent> _children = new ArrayList<>();
    
    private final Set<String> stateKeys = new HashSet<>(); 

    private final AtomicReference<ComponentState> _state =
            new AtomicReference<>(ComponentState.UNMOUNTED);
    
    // Snapshots
    private final Map<String, Integer> _structureHashes = new HashMap<>();
    private final Map<String, Object> _simpleSnapshots = new HashMap<>();
    private final Map<String, Integer> _identitySnapshots = new HashMap<>();
    private static final Map<Class<?>, String> RESOURCE_CACHE = new ConcurrentHashMap<>();

    private String slotHtml = "";
    
    private boolean _initialized = false;
    
    String _getBundledResources() {
        return RESOURCE_CACHE.computeIfAbsent(this.getClass(), clazz -> {
            StringBuilder bundle = new StringBuilder();
            String baseName = clazz.getSimpleName(); // Ej: "UserPage"

            // 1. Intentar cargar CSS (UserPage.css)
            try (InputStream is = clazz.getResourceAsStream(baseName + ".css")) {
                if (is != null) {
                    String css = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    // Inyectamos con un data-resource para facilitar depuración en DevTools
                    bundle.append("\n<style data-resource=\"").append(baseName).append("\">\n")
                          .append(css)
                          .append("\n</style>\n");
                }
            } catch (Exception e) {
                System.err.println("⚠️ JReactive: Error leyendo CSS para " + baseName + ": " + e.getMessage());
            }

            // 2. Intentar cargar JS (UserPage.js)
            try (InputStream is = clazz.getResourceAsStream(baseName + ".js")) {
                if (is != null) {
                    String js = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    bundle.append("\n<script data-resource=\"").append(baseName).append("\">\n")
                          .append("/*<![CDATA[*/\n")
                          .append(js)
                          .append("\n/*]]>*/\n")
                          .append("\n</script>\n");
                }
            } catch (Exception e) {
                System.err.println("⚠️ JReactive: Error leyendo JS para " + baseName + ": " + e.getMessage());
            }

            return bundle.toString();
        });
    }
    
    public synchronized void _captureStateSnapshot() {
        if (map == null) buildBindings();
        
        _structureHashes.clear();
        _simpleSnapshots.clear();

        for (String key : stateKeys) {
            try {
                Object val = getFieldValueByName(key);
                if (val == null) {
                    _simpleSnapshots.put(key, null);
                } else if (val instanceof String || val instanceof Number || val instanceof Boolean) {
                    _simpleSnapshots.put(key, val);
                } else if (!(val instanceof SmartList || val instanceof SmartSet || val instanceof SmartMap)) {
                    // Es un POJO: guardamos el hash de sus campos
                    _structureHashes.put(key, getPojoHash(val));
                }
            } catch (Exception ignored) {}
        }
        for (HtmlComponent child : _children()) {
            child._captureStateSnapshot();
        }
    }

    void _setSlotHtml(String html) {
        this.slotHtml = (html == null) ? "" : html;
    }

    protected String slot() {
        return slotHtml;
    }
    
    // 🔥 FIX: Sincronizado por seguridad en la gestión de hijos
    void _addChild(HtmlComponent child) { 
        synchronized(_children) {
            _children.add(child); 
        }
    }

    List<HtmlComponent> _children() { 
        synchronized(_children) {
            return new ArrayList<>(_children); 
        }
    }
    
    public void _initIfNeeded() {
        if (!_initialized) {
            onInit();
            _initialized = true;
        }
    }

    protected void onMount() {}
    protected void onUnmount() {}
    protected void onInit() {}
    
    public void _mountRecursive() {
        if (_state.compareAndSet(ComponentState.UNMOUNTED, ComponentState.MOUNTED)) {
            onMount();
        }
        for (HtmlComponent child : _children()) {
            child._mountRecursive();
        }
    }

    public void _unmountRecursive() {
        for (HtmlComponent child : _children()) {
            child._unmountRecursive();
        }
        if (_state.compareAndSet(ComponentState.MOUNTED, ComponentState.UNMOUNTED)) {
            onUnmount();
            cleanupBindings();
        }
    }

    ComponentState _state() {
        return _state.get();
    }
    
    private void cleanupBindings() {
        Map<String, ReactiveVar<?>> binds = getRawBindings();
        binds.values().forEach(rx -> rx.clearListeners());
    }

    @Override
    public Map<String, ReactiveVar<?>> bindings() {
        if (cached == null) {
            synchronized(this) {
                if (cached == null) cached = ComponentEngine.render(this);
            }
        }
        return cached.bindings();
    }

    @Override
    public String render() {
        if (cached == null) {
            synchronized(this) {
                if (cached == null) cached = ComponentEngine.render(this);
            }
        }
        return cached.html();
    }

    protected abstract String template();

    public synchronized Map<String, ReactiveVar<?>> getRawBindings() {
        if (map == null) buildBindings();
        return map;
    }

    // 🔥 FIX: buildBindings debe ser atómico para no duplicar llaves en stateKeys
    private synchronized void buildBindings() {
        if (map != null) return;
        map = new HashMap<>();
        stateKeys.clear();

        Class<?> c = getClass();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                Bind bindAnn    = f.getAnnotation(Bind.class);
                State stateAnn = f.getAnnotation(State.class);

                if (bindAnn == null && stateAnn == null) continue;

                f.setAccessible(true);
                try {
                    Object raw = f.get(this);

                    ReactiveVar<?> rx;
                    if (bindAnn != null) {
                        rx = (raw instanceof ReactiveVar<?> r) ? r :
                             (raw instanceof Type<?> v)        ? v.rx() :
                             new ReactiveVar<>(raw);

                        String key = bindAnn.value().isBlank() ? f.getName() : bindAnn.value();
                        rx.setActiveGuard(() -> _state() == ComponentState.MOUNTED);
                        map.put(key, rx);
                    }

                    if (stateAnn != null) {
                        Object smartValue = wrapInSmartType(raw);

                        if (smartValue != raw) {
                            f.set(this, smartValue);
                            raw = smartValue; 
                        }

                        ReactiveVar<Object> srx = new ReactiveVar<>(raw);
                        srx.setActiveGuard(() -> _state() == ComponentState.MOUNTED);

                        srx.onChange(newValue -> {
                            try {
                                f.setAccessible(true);
                                Object smartNew = wrapInSmartType(newValue);
                                f.set(this, smartNew);
                            } catch (Exception e) {
                                System.err.println("Error reflexion writing field " + f.getName() + ": " + e.getMessage());
                            }
                        });

                        String key = stateAnn.value().isBlank() ? f.getName() : stateAnn.value();
                        map.put(key, srx);
                        stateKeys.add(key);
                    }

                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
            c = c.getSuperclass();
        }
    }

    // 🔥 FIX: Sincronizado para asegurar consistencia entre el valor real y el ReactiveVar
    public synchronized void _syncState() {
        if (map == null) buildBindings();

        for (String key : stateKeys) {
            try {
                @SuppressWarnings("unchecked")
                ReactiveVar<Object> rx = (ReactiveVar<Object>) map.get(key);
                if (rx == null) continue;

                Object newValue = getFieldValueByName(key);

                if (!hasChanged(key, newValue)) {
                    continue; 
                }

                rx.set(newValue);
                
            } catch (Exception e) {
                System.err.println("Error Smart-Sync '" + key + "': " + e.getMessage());
            }
        }
        
        for (HtmlComponent child : _children()) {
            child._syncState();
        }
        
    }

    private boolean hasChanged(String key, Object newVal) {
        // 1. Si es un SmartType (List/Map/Set), los deltas ya se manejan por eventos
        if (newVal instanceof SmartList<?> || newVal instanceof SmartSet<?> || newVal instanceof SmartMap<?,?>) {
            return false;
        }

        // 2. Para tipos simples y POJOs, usamos la comparación de contenido (equals)
        // En lugar de guardar el identityHashCode, guardamos el valor anterior o su snapshot
        Object oldVal = _simpleSnapshots.get(key); 
        
        // Si el objeto es complejo (POJO), guardamos su hash de contenido (POJO Hash)
        if (newVal != null && !(newVal instanceof String || newVal instanceof Number || newVal instanceof Boolean)) {
            int newHash = getPojoHash(newVal);
            Integer oldHash = _structureHashes.get(key);
            return oldHash == null || oldHash != newHash;
        }

        return !Objects.equals(newVal, oldVal);
    }

    private Object getFieldValueByName(String key) throws Exception {
        Class<?> c = getClass();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                State ann = f.getAnnotation(State.class);
                if (ann != null) {
                    String annVal = ann.value().isBlank() ? f.getName() : ann.value();
                    if (annVal.equals(key)) {
                        f.setAccessible(true);
                        return f.get(this);
                    }
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }
    
    public Map<String, Method> getCallableMethods() {
        Map<String, Method> callables = new HashMap<>();
        Class<?> current = this.getClass();

        // Subimos por la jerarquía para descubrir métodos @Call heredados
        while (current != null && current != HtmlComponent.class) {
            for (Method m : current.getDeclaredMethods()) {
                if (m.isAnnotationPresent(com.ciro.jreactive.annotations.Call.class)) {
                    // putIfAbsent respeta el Polimorfismo: si el hijo sobreescribe, gana el hijo
                    if (!callables.containsKey(m.getName())) {
                        m.setAccessible(true);
                        callables.put(m.getName(), m);
                    }
                }
            }
            current = current.getSuperclass();
        }
        return callables;
    }
    
    void _injectParams(Map<String,String> params) {
        var fields = new java.util.ArrayList<java.lang.reflect.Field>();
        Class<?> c = getClass();
        while (c != null && c != Object.class) {
            for (var f : c.getDeclaredFields()) fields.add(f);
            c = c.getSuperclass();
        }
        for (var f : fields) {
            var ann = f.getAnnotation(com.ciro.jreactive.router.Param.class);
            if (ann == null) continue;
            String name = ann.value();
            if (!params.containsKey(name)) continue;
            String raw = params.get(name);
            try {
                f.setAccessible(true);
                Class<?> t = f.getType();
                Object cur = f.get(this);
                Object val = convertParam(raw, t, cur);
                f.set(this, val);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static Object convertParam(String raw, Class<?> target, Object current) {
        if (target == String.class) return raw;
        if (target == int.class || target == Integer.class) return Integer.parseInt(raw);
        if (target == long.class || target == Long.class) return Long.parseLong(raw);
        if (target == boolean.class || target == Boolean.class) return Boolean.parseBoolean(raw);

        if (ReactiveVar.class.isAssignableFrom(target)) {
            @SuppressWarnings("unchecked")
            ReactiveVar<Object> rv = (ReactiveVar<Object>) current;
            rv.set(raw);
            return rv;
        }
        if (Type.class.isAssignableFrom(target)) {
            @SuppressWarnings("unchecked")
            Type<Object> tp = (Type<Object>) current;
            tp.set(raw);
            return tp;
        }
        try { return target.getConstructor(String.class).newInstance(raw); }
        catch (Exception ignore) {}
        return raw;
    }
    
    @SuppressWarnings("unchecked")
    public <T extends HtmlComponent> T findChild(String ref, Class<T> type) {
        for (HtmlComponent child : _children()) {
            // En JReactive, el atributo 'ref' del template se convierte en el ID del componente
            if (ref.equals(child.getId()) && type.isInstance(child)) {
                return (T) child;
            }
            // Búsqueda recursiva en profundidad
            T found = child.findChild(ref, type);
            if (found != null) return found;
        }
        return null;
    }
    
    @SuppressWarnings("unchecked")
    protected synchronized void updateState(String fieldName) {
        try {
            Class<?> c = getClass();
            Field found = null;
            while (c != null && c != Object.class) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.getName().equals(fieldName) && f.getAnnotation(State.class) != null) {
                        found = f;
                        break;
                    }
                }
                if (found != null) break;
                c = c.getSuperclass();
            }
            if (found == null) {
                throw new IllegalArgumentException("No @State field named '" + fieldName + "' in " + getClass());
            }

            found.setAccessible(true);
            Object currentValue = found.get(this);

            State stateAnn = found.getAnnotation(State.class);
            String bindingKey = stateAnn.value().isBlank() ? found.getName() : stateAnn.value();

            ReactiveVar<Object> rx = (ReactiveVar<Object>) getRawBindings().get(bindingKey);
            if (rx == null) {
                throw new IllegalStateException("No ReactiveVar for @State '" + bindingKey + "'");
            }
            rx.set(currentValue);

        } catch (Exception e) {
            throw new RuntimeException("Error updating @State '" + fieldName + "'", e);
        }
    }
    
    private Object wrapInSmartType(Object rawValue) {
        if (rawValue == null) return null;
        if (rawValue instanceof java.util.List<?> list) {
            if (list instanceof SmartList) return list;
            return new SmartList<>(list);
        }
        if (rawValue instanceof java.util.Set<?> set) {
            if (set instanceof SmartSet) return set;
            return new SmartSet<>(set);
        }
        if (rawValue instanceof java.util.Map<?,?> map) {
            if (map instanceof SmartMap) return map;
            return new SmartMap<>(map);
        }
        return rawValue;
    }
    
    private int getPojoHash(Object o) {
        if (o == null) return 0;
        // Tipos básicos: usar su hash natural
        if (o instanceof String || o instanceof Number || o instanceof Boolean || o instanceof Enum) {
            return o.hashCode();
        }
        // Colecciones: usar su hash (que ya es profundo en Java)
        if (o instanceof Collection || o instanceof Map) {
            return o.hashCode();
        }
        
        int result = 1;
        try {
            Class<?> curr = o.getClass();
            // Límite de seguridad para no ciclar infinitamente en grafos complejos
            if (curr.getName().startsWith("java.")) return o.hashCode();

            while (curr != null && curr != Object.class) {
                for (Field f : curr.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getName().startsWith("this$")) continue;
                    
                    f.setAccessible(true);
                    Object val = f.get(o);
                    
                    int fieldHash = 0;
                    if (val != null) {
                        // 🔥 RECURSIÓN REAL: Llamamos a getPojoHash de nuevo
                        fieldHash = getPojoHash(val); 
                    }
                    result = 31 * result + fieldHash;
                }
                curr = curr.getSuperclass();
            }
        } catch (Exception e) {
            return o.hashCode();
        }
        return result;
    }

    /**
     * Calcula qué campos @State han cambiado y devuelve un mapa con los deltas.
     * Útil para optimizar el tráfico de componentes @Client.
     */
    public synchronized Map<String, Object> _getStateDeltas() {
        Map<String, Object> deltas = new HashMap<>();
        for (String key : stateKeys) {
            try {
                Object newValue = getFieldValueByName(key);
                if (hasChanged(key, newValue)) {
                    deltas.put(key, newValue);
                }
            } catch (Exception ignored) {}
        }
        return deltas;
    }
    
    public long _getVersion() { return _version; }
    public void _setVersion(long v) { this._version = v; }
}