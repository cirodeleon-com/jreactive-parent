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
import java.util.concurrent.locks.ReentrantLock;

import org.intellij.lang.annotations.Language;

import java.util.Collection;
import java.util.Objects;

import com.ciro.jreactive.annotations.Prop;
import com.ciro.jreactive.smart.SmartList;
import com.ciro.jreactive.smart.SmartSet;
import com.ciro.jreactive.spi.AccessorRegistry;
import com.ciro.jreactive.spi.ComponentAccessor;
import com.ciro.jreactive.smart.SmartMap;

public abstract class HtmlComponent extends ViewLeaf implements java.io.Serializable {
	
	private volatile long _version = 0;

    private Map<String, ReactiveVar<?>> map;
    private transient ComponentEngine.Rendered cached;
    private final List<HtmlComponent> _children = new ArrayList<>();
 

 // ──────────────────────────────────────────────────────────────
 // 🔥 AOT FIX: Pool de reciclaje por render + secuencia estable IDs
 // ──────────────────────────────────────────────────────────────
 private transient List<HtmlComponent> _renderPool = null;  // hijos del render anterior (para reuse)
 private final transient Map<String, Integer> _childIdSeq = new HashMap<>(); // className -> seq estable
//✅ Alias local -> ref real (namespaced) para hijos
private final  Map<String, String> _childRefAlias = new HashMap<>();




    
    private final Set<String> stateKeys = new HashSet<>(); 
    
    

    private final transient AtomicReference<ComponentState> _state =
            new AtomicReference<>(ComponentState.UNMOUNTED);
    
    // Snapshots
    private final Map<String, Integer> _structureHashes = new HashMap<>();
    private final Map<String, Object> _simpleSnapshots = new HashMap<>();
    private final Map<String, Integer> _identitySnapshots = new HashMap<>();
    private static final Map<Class<?>, String> RESOURCE_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, String> TEMPLATE_HTML_CACHE = new ConcurrentHashMap<>();
    
    
  //🔥 Cachés Estáticos de Reflexión (Se ejecutan 1 sola vez en la vida del Servidor)
    private static final Map<Class<?>, List<Field>> FIELDS_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, Method>> CALLABLES_CACHE = new ConcurrentHashMap<>();

    private String slotHtml = "";
    private Map<String, String> _slots = new HashMap<>();
    
    private boolean _initialized = false;
    
    // 🔒 Lock para gestión de estado y árbol (Anti-Pinning)
    private transient volatile ReentrantLock lock;
    
 
    private ReentrantLock getLock() {
        ReentrantLock result = lock;
        if (result == null) {
            synchronized (this) {
                result = lock;
                if (result == null) {
                    lock = result = new ReentrantLock();
                }
            }
        }
        return result;
    }
    
    public String _getBundledResources() {
        return RESOURCE_CACHE.computeIfAbsent(this.getClass(), clazz -> {
            StringBuilder bundle = new StringBuilder();
            String baseName = clazz.getSimpleName();

            // 🔥 1. MAGIA AOT: Obtenemos el CSS ya con scope y minificado desde el Accessor (O(1))
            @SuppressWarnings({"rawtypes", "unchecked"})
            com.ciro.jreactive.spi.ComponentAccessor acc = com.ciro.jreactive.spi.AccessorRegistry.get((Class) clazz);
            if (acc != null) {
                String scopedCss = acc.getScopedCss();
                if (scopedCss != null && !scopedCss.isBlank()) {
                    bundle.append("\n<style data-resource=\"").append(baseName).append("\">\n")
                          .append(scopedCss)
                          .append("\n</style>");
                }
            }

            // 2. JS: Se lee normal desde el disco (Se mantiene igual)
            try (InputStream is = clazz.getResourceAsStream(baseName + ".js")) {
                if (is != null) {
                    String js = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    bundle.append("\n<script data-resource=\"").append(baseName).append("\">\n")
                          .append("/*<![CDATA[*/\n")
                          .append(js)
                          .append("\n/*]]>*/\n")
                          .append("</script>\n");
                }
            } catch (Exception e) {
                System.err.println("⚠️ JReactive: Error leyendo JS para " + baseName + ": " + e.getMessage());
            }

            return bundle.toString();
        });
    }
    
    public String _getScopeId() {
        return "jrx-sc-" + this.getClass().getSimpleName();
    }
    
    public void _captureStateSnapshot() {
    	getLock().lock();
        try {
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
                    } else {
                        // 🔥 FIX: Faltaba este 'else'. Es un POJO: guardamos el hash de sus campos
                        _structureHashes.put(key, getPojoHash(val));
                    }
                } catch (Exception e) {
                    System.err.println("⚠️ [JReactive] Error capturando snapshot de '@State " + key + "': " + e.getMessage());
                }
            }
            
            // Recurrimos sobre una copia segura de la lista de hijos
            List<HtmlComponent> safeChildren = new ArrayList<>(_children);
            for (HtmlComponent child : safeChildren) {
                child._captureStateSnapshot();
            }
        } finally {
        	getLock().unlock();
        }
    }

    
    
    /**
     * Agrega un hijo de forma segura usando el lock del componente.
     * Vital para el reciclaje de componentes en modo AOT.
     */
    public void addChild(HtmlComponent child) {
    	getLock().lock();
        try {
            if (!this._children.contains(child)) {
                this._children.add(child);
            }
        } finally {
        	getLock().unlock();
        }
    }
    
    void _addChild(HtmlComponent child) { 
    	getLock().lock();
        try {
            _children.add(child); 
        } finally {
        	getLock().unlock();
        }
    }
    
    

    List<HtmlComponent> _children() { 
    	getLock().lock();
        try {
            return new ArrayList<>(_children); 
        } finally {
        	getLock().unlock();
        }
    }
    
    public void _setSlots(Map<String, String> slots) {
        this._slots = (slots == null) ? new HashMap<>() : slots;
    }

    public String _getSlotHtml(String name) {
        if (name == null || name.isBlank()) name = "default";
        return _slots.getOrDefault(name, "");
    }

    public String _getSlotHtml() {
        return _getSlotHtml("default");
    }

    protected String slot() {
        return _getSlotHtml("default");
    }
    
    protected String slot(String name) {
        return _getSlotHtml(name);
    }
    
    public String renderChild(String className, Map<String, String> attrs, Map<String, String> slots) {
        if (attrs != null) {
            String ref = attrs.get("ref");
            if (ref != null && !ref.isBlank()) {
                String qualified = _qualifyChildRef(ref);
                if (!qualified.equals(ref)) {
                    _childRefAlias.put(ref, qualified);
                    attrs.put("ref", qualified);
                }
            }
        }
        return ComponentEngine.renderChild(this, className, attrs, slots);
    }

    
 // ──────────────────────────────────────────────────────────────
 // 🔥 AOT FIX: ciclo de render (drain -> reuse -> dispose)
 // Nota: DEBE ser public porque el Accessor AOT se genera en el paquete
//        del componente (no necesariamente com.ciro.jreactive).
 // ──────────────────────────────────────────────────────────────
 public void _beginRenderCycle() {
	 getLock().lock();
     try {
         // defensivo por reentrancia
         if (_renderPool != null) return;

         // Pool = hijos anteriores (reusables)
         _renderPool = new ArrayList<>(_children);

         // Limpiamos la lista real: este render reconstruye el árbol
         _children.clear();
         
         _childIdSeq.clear();
         
         _childRefAlias.clear();
      
         
     } finally {
    	 getLock().unlock();
     }
 }

 public void _endRenderCycle() {
     List<HtmlComponent> pool;
     getLock().lock();
     try {
         pool = _renderPool;
         _renderPool = null;
     } finally {
    	 getLock().unlock();
     }

     // Desmontar lo NO reutilizado (lo que quedó en pool)
     if (pool != null) {
         for (HtmlComponent z : pool) {
             try { z._unmountRecursive(); } catch (Throwable ignored) {}
         }
         pool.clear();
     }
 }

 /** Pool actual (solo existe durante render()).
  *  OJO: se devuelve la referencia para que el engine pueda "consumir" (remove) reusados.
  */
 public List<HtmlComponent> _getRenderPool() {
     return _renderPool;
 }

 /** Secuencia estable por tipo de hijo (solo se incrementa al CREAR, no al reusar) */
 public int _nextChildIdSeq(String className) {
	 getLock().lock();
     try {
         int n = _childIdSeq.getOrDefault(className, 0);
         _childIdSeq.put(className, n + 1);
         return n;
     } finally {
    	 getLock().unlock();
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
    
    

    public ComponentState _state() {
        return _state.get();
    }
    
    private void cleanupBindings() {
        Map<String, ReactiveVar<?>> binds = getRawBindings();
        binds.values().forEach(rx -> rx.clearListeners());
    }

    /*
    @Override
    public Map<String, ReactiveVar<?>> bindings() {
        if (cached == null) {
            // Doble check locking seguro con synchronized (esto es rápido, no hace I/O)
            synchronized(this) {
                if (cached == null) cached = ComponentEngine.render(this);
            }
        }
        return cached.bindings();
    }
    */
    
    @Override
    public Map<String, ReactiveVar<?>> bindings() {
        // ✅ Importante: forzamos pasar por render(), así AOT + reciclaje aplica siempre
        //if (cached == null) render();
        //return cached.bindings();
    	
        if (cached != null) return cached.bindings();
        
        // 2. 🔥 FIX MAESTRO: Si no hay caché (ej: venimos de Redis), 
        // devolvemos los bindings por reflexión (getRawBindings) 
        // EN LUGAR de forzar un render(). 
        // Esto evita destruir y recrear los hijos (lo que dañaba el reloj y refs).
        return getRawBindings();
    }


    @Override
    public String render() {
        if (cached != null) return cached.html();

        getLock().lock(); // 🔒 Usamos el cerrojo universal del componente
        try {
            // Doble validación por si otro hilo ya lo renderizó mientras esperábamos
            if (cached != null) return cached.html(); 

            _beginRenderCycle();
            try {
                this.cached = ComponentEngine.render(this);
                return cached.html();
            } finally {
                _endRenderCycle();
            }
        } finally {
            getLock().unlock(); // 🔓 Liberamos siempre, pase lo que pase
        }
    }

    @Language("html")
    protected String template() {
        return TEMPLATE_HTML_CACHE.computeIfAbsent(this.getClass(), clazz -> {
            String baseName = clazz.getSimpleName() + ".html";
            try (InputStream is = clazz.getResourceAsStream(baseName)) {
                if (is != null) {
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                System.err.println("⚠️ [JReactive] Error leyendo template externo " + baseName + ": " + e.getMessage());
            }
            throw new IllegalStateException("JReactive: Debes sobrescribir el método template() o crear el archivo " + baseName + " junto a la clase " + clazz.getSimpleName());
        });
    }

    public Map<String, ReactiveVar<?>> getRawBindings() {
    	getLock().lock();
        try {
            if (map == null) buildBindings();
            return map;
        } finally {
        	getLock().unlock();
        }
    }

    private void buildBindings() {
        if (map != null) return;
        map = new HashMap<>();
        stateKeys.clear();

        List<Field> fields = FIELDS_CACHE.computeIfAbsent(this.getClass(), clazz -> {
            List<Field> list = new ArrayList<>();
            Class<?> c = clazz;
            while (c != null && c != Object.class) {
                for (Field f : c.getDeclaredFields()) {
                    // 🔥 MODO DUAL: Escaneamos las 3 anotaciones
                    if (f.isAnnotationPresent(Bind.class) || 
                        f.isAnnotationPresent(State.class) || 
                        f.isAnnotationPresent(Prop.class)) {
                        f.setAccessible(true);
                        list.add(f);
                    }
                }
                c = c.getSuperclass();
            }
            return list;
        });

        for (Field f : fields) {
            Bind bindAnn = f.getAnnotation(Bind.class);
            State stateAnn = f.getAnnotation(State.class);
            Prop propAnn = f.getAnnotation(Prop.class); 

            try {
                Object raw = f.get(this);

                // --- 1. MODO LEGACY: @Bind (Two-Way Binding) ---
                if (bindAnn != null) {
                    ReactiveVar<?> rx = (raw instanceof ReactiveVar<?> r) ? r :
                                        (raw instanceof Type<?> v)        ? v.rx() :
                                        new ReactiveVar<>(raw);
                    rx.setGenericType(extractRealType(f.getGenericType()));
                    String key = bindAnn.value().isBlank() ? f.getName() : bindAnn.value();
                    rx.setActiveGuard(() -> _state() == ComponentState.MOUNTED);
                    map.put(key, rx);
                }

                // --- 2. MODO NUEVO: @Prop (One-Way Plano) ---
                if (propAnn != null) {
                    ReactiveVar<Object> prx = new ReactiveVar<>(raw);
                    prx.setGenericType(extractRealType(f.getGenericType()));
                    String key = propAnn.value().isBlank() ? f.getName() : propAnn.value();
                    prx.setActiveGuard(() -> _state() == ComponentState.MOUNTED);
                    
                    prx.onChange(newValue -> {
                        try {
                            f.setAccessible(true);
                            Object smartNew = wrapInSmartType(newValue);
                            f.set(this, smartNew);
                        } catch (Exception e) {}
                    });
                    
                    map.put(key, prx);
                    // 🚫 IMPORTANTE: NO lo agregamos a stateKeys (es de solo lectura)
                }

                // --- 3. MODO ESTADO: @State (Estado interno) ---
                if (stateAnn != null) {
                    Object smartValue = wrapInSmartType(raw);
                    if (smartValue != raw) {
                        f.set(this, smartValue);
                        raw = smartValue; 
                    }
                    ReactiveVar<Object> srx = new ReactiveVar<>(raw);
                    srx.setGenericType(extractRealType(f.getGenericType()));
                    srx.setActiveGuard(() -> _state() == ComponentState.MOUNTED);

                    srx.onChange(newValue -> {
                        try {
                            f.setAccessible(true); 
                            Object smartNew = wrapInSmartType(newValue);
                            f.set(this, smartNew);
                        } catch (Exception e) {}
                    });

                    String key = stateAnn.value().isBlank() ? f.getName() : stateAnn.value();
                    map.put(key, srx);
                    stateKeys.add(key); // ✅ Este SÍ va al escáner de cambios
                }

            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void _syncState() {
    	getLock().lock();
        try {
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
                    //this.cached = null;
                } catch (Exception e) {
                    System.err.println("Error Smart-Sync '" + key + "': " + e.getMessage());
                }
            }
            
            // Recurrimos sobre una copia segura
            List<HtmlComponent> safeChildren = new ArrayList<>(_children);
            for (HtmlComponent child : safeChildren) {
                child._syncState();
            }
        } finally {
        	getLock().unlock();
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
                State stateAnn = f.getAnnotation(State.class);
                Bind bindAnn = f.getAnnotation(Bind.class);
                Prop propAnn = f.getAnnotation(Prop.class); // 🔥
                
                if (stateAnn != null || bindAnn != null || propAnn != null) {
                    String annVal = "";
                    if (stateAnn != null && !stateAnn.value().isBlank()) annVal = stateAnn.value();
                    else if (bindAnn != null && !bindAnn.value().isBlank()) annVal = bindAnn.value();
                    else if (propAnn != null && !propAnn.value().isBlank()) annVal = propAnn.value();
                    else annVal = f.getName();

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
        return CALLABLES_CACHE.computeIfAbsent(this.getClass(), clazz -> {
            Map<String, Method> callables = new HashMap<>();
            Class<?> current = clazz;
            while (current != null && current != HtmlComponent.class) {
                for (Method m : current.getDeclaredMethods()) {
                    if (m.isAnnotationPresent(com.ciro.jreactive.annotations.Call.class)) {
                        if (!callables.containsKey(m.getName())) {
                            m.setAccessible(true);
                            callables.put(m.getName(), m);
                        }
                    }
                }
                current = current.getSuperclass();
            }
            return callables;
        });
    }
    
    void _injectParams(Map<String,String> params) {
        var fields = new java.util.ArrayList<java.lang.reflect.Field>();
        Class<?> c = getClass();
        while (c != null && c != Object.class) {
            for (var f : c.getDeclaredFields()) fields.add(f);
            c = c.getSuperclass();
        }
        for (var f : fields) {
            var ann = f.getAnnotation(com.ciro.jreactive.router.UrlVariable.class);
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
    
 // Añadir en HtmlComponent.java
    void _injectQueryParams(Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) return;

        var fields = new java.util.ArrayList<java.lang.reflect.Field>();
        Class<?> c = getClass();
        while (c != null && c != Object.class) {
            for (var f : c.getDeclaredFields()) fields.add(f);
            c = c.getSuperclass();
        }
        
        for (var f : fields) {
            var ann = f.getAnnotation(com.ciro.jreactive.router.UrlParam.class);
            if (ann == null) continue;
            
            // Si el valor está vacío, usa el nombre de la variable
            String name = ann.value().isBlank() ? f.getName() : ann.value();
            if (!queryParams.containsKey(name)) continue;
            
            String raw = queryParams.get(name);
            try {
                f.setAccessible(true);
                Class<?> t = f.getType();
                Object cur = f.get(this);
                Object val = convertParam(raw, t, cur);
                f.set(this, val);
            } catch (Exception e) {
                throw new RuntimeException("Error inyectando @UrlParam: " + name, e);
            }
        }
    }
    
 // Añadir en HtmlComponent.java
    public java.util.Map<String, String> _getUrlBindings() {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        Class<?> c = getClass();
        while (c != null && c != Object.class) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                com.ciro.jreactive.router.UrlParam ann = f.getAnnotation(com.ciro.jreactive.router.UrlParam.class);
                if (ann != null) {
                    String paramName = ann.value().isBlank() ? f.getName() : ann.value();
                    map.put(f.getName(), paramName); 
                }
            }
            c = c.getSuperclass();
        }
        return map;
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
        String realRef = _childRefAlias.getOrDefault(ref, ref);

        for (HtmlComponent child : _children()) {
            if (realRef.equals(child.getId()) && type.isInstance(child)) {
                return (T) child;
            }
            T found = child.findChild(ref, type); // pasamos ref local, cada nodo resuelve su mapa
            if (found != null) return found;
        }
        return null;
    }

    
    @SuppressWarnings("unchecked")
    protected void updateState(String fieldName) {
    	getLock().lock();
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
        } finally {
        	getLock().unlock();
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
    public Map<String, Object> _getStateDeltas() {
    	getLock().lock();
        try {
            Map<String, Object> deltas = new HashMap<>();
            for (String key : stateKeys) {
                try {
                    Object newValue = getFieldValueByName(key);
                    if (hasChanged(key, newValue)) {
                        deltas.put(key, newValue);
                    }
                } catch (Exception e) {
                    System.err.println("⚠️ [JReactive] Error calculando Delta para '@State " + key + "': " + e.getMessage());
                }
            }
            return deltas;
        } finally {
        	getLock().unlock();
        }
    }
    
    public long _getVersion() { return _version; }
    public void _setVersion(long v) { this._version = v; }
    
    
    private String _qualifyChildRef(String ref) {
        if (ref == null) return null;
        String r = ref.trim();
        if (r.isEmpty()) return r;

        String prefix = getId() + ".";
        return r.startsWith(prefix) ? r : (prefix + r);
    }


    public void _registerRef(String alias, String realId) {
        if (alias == null || realId == null) return;
        getLock().lock();
        try {
            _childRefAlias.put(alias, realId);
        } finally {
        	getLock().unlock();
        }
    }
    
    public String _resolveRef(String alias) {
    	getLock().lock();
        try {
            return _childRefAlias.get(alias);
        } finally {
        	getLock().unlock();
        }
    }
    
 // 🔥 EL DESENVOLVEDOR: Le quita la cáscara Type<T> para que Jackson vea solo la T
    private java.lang.reflect.Type extractRealType(java.lang.reflect.Type declaredType) {
        if (declaredType instanceof java.lang.reflect.ParameterizedType pt) {
            java.lang.reflect.Type rawType = pt.getRawType();
            if (rawType == com.ciro.jreactive.Type.class || rawType == com.ciro.jreactive.ReactiveVar.class) {
                return pt.getActualTypeArguments()[0]; // Devuelve el tipo que está adentro
            }
        }
        return declaredType; // Si es una List<UserData> normal, la deja intacta
    }
    
   
    
}