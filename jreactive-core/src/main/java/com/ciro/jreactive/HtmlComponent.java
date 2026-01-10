package com.ciro.jreactive;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet; // [Nuevo]
import java.util.List;
import java.util.Map;
import java.util.Set;     // [Nuevo]
import java.util.concurrent.atomic.AtomicReference;
import java.util.Collection;
import java.util.Objects;
import com.ciro.jreactive.smart.SmartList;
import com.ciro.jreactive.smart.SmartSet;
import com.ciro.jreactive.smart.SmartMap;

public abstract class HtmlComponent extends ViewLeaf {

    private Map<String, ReactiveVar<?>> map;      // se crea on-demand
    private ComponentEngine.Rendered cached;
    private final List<HtmlComponent> _children = new ArrayList<>();
    
    // [Nuevo] Rastreador de keys de estado para auto-sync
    private final Set<String> stateKeys = new HashSet<>(); 

    private final AtomicReference<ComponentState> _state =
            new AtomicReference<>(ComponentState.UNMOUNTED);
    
 // 📸 Almacenes temporales para detectar cambios (Snapshots)
    private final Map<String, Integer> _structureHashes = new HashMap<>();
    private final Map<String, Object> _simpleSnapshots = new HashMap<>();
    
    private final Map<String, Integer> _identitySnapshots = new HashMap<>();
 // ... snapshots ...
    
    
    
    /** Contenido raw que va entre <MiComp> ... </MiComp> */
    private String slotHtml = "";
    
    
    
    /**
     * Captura el estado actual. Soporta: List, Set, Queue, Map y POJOs.
     */
    public void _captureStateSnapshot() {
        if (map == null) buildBindings();
        
        _structureHashes.clear();
        _simpleSnapshots.clear();
        _identitySnapshots.clear();

        for (String key : stateKeys) {
            try {
                Object val = getFieldValueByName(key);
                
                _identitySnapshots.put(key, System.identityHashCode(val));
                
                if (val == null) {
                    _simpleSnapshots.put(key, null);
                } 
                // A) Familia Smart (List, Set, Map) -> No guardamos nada, confiamos en isDirty()
                else if (val instanceof SmartList || val instanceof SmartSet || val instanceof SmartMap) {
                    // No hacemos nada, el flag dirty se encarga
                }
                // B) Tipos inmutables simples (String, Integer, Boolean) -> Guardamos valor
                else if (val instanceof String || val instanceof Number || val instanceof Boolean) {
                    _simpleSnapshots.put(key, val);
                }
                // C) POJOs Mutables (PageState, SignupForm) y Colecciones normales -> Guardamos DEEP HASH
                else {
                    _structureHashes.put(key, getPojoHash(val));
                }
            } catch (Exception e) {
                // Ignorar
            }
        }
    }

    // Solo el motor debe usar esto
    void _setSlotHtml(String html) {
        this.slotHtml = (html == null) ? "" : html;
    }

    /**
     * Para que los componentes contenedores (JForm, layouts, etc.)
     * puedan incrustar el contenido interno.
     */
    protected String slot() {
        return slotHtml;
    }
    
    void _addChild(HtmlComponent child) { _children.add(child); }
    List<HtmlComponent> _children()     { return _children; }
     // para IDs automáticos
    

    /** Hook: se llama una vez cuando el componente pasa a MOUNTED */
    protected void onMount() {
        // por defecto nada; las subclases sobreescriben si necesitan
    }

    /** Hook: se llama una vez cuando el componente pasa a UNMOUNTED */
    protected void onUnmount() {
        // por defecto nada
    }
    
    /** Monta este componente (si aún no lo está) y luego sus hijos */
    public void _mountRecursive() {
        // primero este componente
        if (_state.compareAndSet(ComponentState.UNMOUNTED, ComponentState.MOUNTED)) {
            onMount();
        }
        // luego los hijos
        for (HtmlComponent child : _children) {
            child._mountRecursive();
        }
    }

    /** Desmonta en cascada: hijos primero, luego este componente */
    public void _unmountRecursive() {
        // primero los hijos
        for (HtmlComponent child : _children) {
            child._unmountRecursive();
        }
        // luego este
        if (_state.compareAndSet(ComponentState.MOUNTED, ComponentState.UNMOUNTED)) {
            onUnmount();
            cleanupBindings();
        }
    }

    /** Solo por si quieres inspeccionar el estado en debug */
    ComponentState _state() {
        return _state.get();
    }
    
    private void cleanupBindings() {
        // selfBindings() se asegura de construir `map` si aún no existe
        Map<String, ReactiveVar<?>> binds = selfBindings();
        binds.values().forEach(rx -> rx.clearListeners());
    }


    /* --------------------- API ViewLeaf -------------------- */
    @Override
    public Map<String, ReactiveVar<?>> bindings() {
        if (cached == null) cached = ComponentEngine.render(this);
        return cached.bindings();
    }

    @Override
    public String render() {
        if (cached == null) cached = ComponentEngine.render(this);
        return cached.html();
    }

    /* ----------------- para las subclases ------------------ */
    protected abstract String template();

    /* ----------------- util interno ------------------------ */
    Map<String, ReactiveVar<?>> selfBindings() {
        if (map == null) buildBindings();
        return map;
    }

    private void buildBindings() {
        map = new HashMap<>();
        stateKeys.clear(); // [Nuevo] Limpiamos para reconstruir

        Class<?> c = getClass();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                Bind bindAnn   = f.getAnnotation(Bind.class);
                State stateAnn = f.getAnnotation(State.class);

                if (bindAnn == null && stateAnn == null) continue;

                f.setAccessible(true);
                try {
                    Object raw = f.get(this);

                    ReactiveVar<?> rx;
                    if (bindAnn != null) {
                        // comportamiento actual de @Bind
                        rx = (raw instanceof ReactiveVar<?> r) ? r :
                             (raw instanceof Type<?> v)        ? v.rx() :
                             new ReactiveVar<>(raw);

                        String key = bindAnn.value().isBlank() ? f.getName() : bindAnn.value();
                        rx.setActiveGuard(() -> _state() == ComponentState.MOUNTED);
                        map.put(key, rx);
                    }


                    if (stateAnn != null) {
                        // 👇 1. INTERCEPTAMOS: Convertimos a Smart si es necesario
                        // (Aquí un HashSet normal se vuelve SmartSet silenciosamente)
                        Object smartValue = wrapInSmartType(raw);

                        // Si hubo conversión, inyectamos el espía en la variable del usuario
                        if (smartValue != raw) {
                            f.set(this, smartValue);
                            raw = smartValue; 
                        }

                        // Creamos el ReactiveVar con el valor Smart
                        ReactiveVar<Object> srx = new ReactiveVar<>(raw);
                        srx.setActiveGuard(() -> _state() == ComponentState.MOUNTED);

                        // 👇 2. PROTEGEMOS EL TWO-WAY BINDING
                        srx.onChange(newValue -> {
                            try {
                                f.setAccessible(true);
                                // Si el WebSocket manda una colección nueva pura, la volvemos a envolver
                                Object smartNew = wrapInSmartType(newValue);
                                f.set(this, smartNew);
                            } catch (Exception e) {
                                System.err.println("Error reflexion writing field " + f.getName() + ": " + e.getMessage());
                            }
                        });

                        String key = stateAnn.value().isBlank() ? f.getName() : stateAnn.value();
                        map.put(key, srx);
                        
                        // Guardamos la key para sincronización automática
                        stateKeys.add(key);
                    }

                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
            c = c.getSuperclass();
        }
    }

    /**
     * Sincronización Inteligente: Solo envía si detecta cambios vs el Snapshot.
     */
    public void _syncState() {
        if (map == null) buildBindings();

        for (String key : stateKeys) {
            try {
                @SuppressWarnings("unchecked")
                ReactiveVar<Object> rx = (ReactiveVar<Object>) map.get(key);
                if (rx == null) continue;

                // 1. Valor fresco post-ejecución
                Object newValue = getFieldValueByName(key);

                // 2. ¿Cambió? (Dirty Checking)
                if (!hasChanged(key, newValue)) {
                    continue; // 🛑 AHORRO: Si es igual, no enviamos nada por red
                }

                // 3. Si cambió, actualizamos el ReactiveVar (esto dispara el envío)
                rx.set(newValue);
                
             // 👇 4. LIMPIEZA POST-ENVÍO (Resetear flags)
             //   if (newValue instanceof SmartList<?> s) s.clearDirty();
             //   else if (newValue instanceof SmartSet<?> s) s.clearDirty();
             //   else if (newValue instanceof SmartMap<?,?> s) s.clearDirty();

            } catch (Exception e) {
                System.err.println("Error Smart-Sync '" + key + "': " + e.getMessage());
            }
        }
    }

    /**
     * Lógica de comparación
     */
private boolean hasChanged(String key, Object newVal) {
	
	Integer oldIdentity = _identitySnapshots.get(key);
    int newIdentity = System.identityHashCode(newVal);
    
    // Si la referencia en memoria es distinta, DEFINITIVAMENTE cambió.
    if (oldIdentity != null && oldIdentity != newIdentity) {
        return true; 
    }
        
        // A) Familia Smart (Optimización O(1))
        if (newVal instanceof SmartList<?> s) return s.isDirty();
        if (newVal instanceof SmartSet<?> s)  return s.isDirty();
        if (newVal instanceof SmartMap<?,?> s) return s.isDirty();

        // B) Tipos Simples Inmutables (String, Integer, Boolean)
        // Estos cambian de referencia, así que equals() funciona perfecto.
        if (newVal instanceof String || newVal instanceof Number || newVal instanceof Boolean) {
            Object oldVal = _simpleSnapshots.get(key);
            return !Objects.equals(newVal, oldVal);
        }

        // C) POJOs Mutables (SignupForm, PageState) y Colecciones Normales
        // Aquí es donde ocurría tu error. Antes usabas equals(), ahora usamos Deep Hash.
        
        // 1. Calculamos la huella digital actual del objeto
        int newHash = getPojoHash(newVal);
        
        // 2. Recuperamos la huella que tomamos en el Snapshot (antes del @Call)
        Integer oldHash = _structureHashes.get(key);
        
        // 3. Si no había huella o es distinta -> ¡CAMBIO DETECTADO!
        return oldHash == null || oldHash != newHash;
    }

    // [Nuevo] Helper para obtener el valor del campo
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
        for (Method m : this.getClass().getDeclaredMethods()) {
            if (m.isAnnotationPresent(com.ciro.jreactive.annotations.Call.class)) {
                m.setAccessible(true);
                callables.put(m.getName(), m);
            }
        }
        return callables;
    }
    
    
    /* ======== Param injection (ruta) ======== */
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
    protected void updateState(String fieldName) {
        try {
            // 1) localizar el Field real
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

            // 🔥 aquí viene el cambio importante:
            State stateAnn = found.getAnnotation(State.class);
            if (stateAnn == null) {
                throw new IllegalStateException("Field '" + fieldName + "' is not annotated with @State");
            }

            String bindingKey = stateAnn.value().isBlank()
                    ? found.getName()
                    : stateAnn.value();

            ReactiveVar<Object> rx = (ReactiveVar<Object>) selfBindings().get(bindingKey);
            if (rx == null) {
                throw new IllegalStateException("No ReactiveVar for @State '" + bindingKey + "'");
            }

            // 2) dispara la notificación
            rx.set(currentValue);

        } catch (Exception e) {
            throw new RuntimeException("Error updating @State '" + fieldName + "'", e);
        }
    }
    
    /**
     * Convierte una colección Java normal en su versión Smart (Espía).
     */
    private Object wrapInSmartType(Object rawValue) {
        if (rawValue == null) return null;

        // 1. LISTAS
        if (rawValue instanceof java.util.List<?> list) {
            if (list instanceof SmartList) return list; // Ya es smart
            return new SmartList<>(list);
        }

        // 2. SETS (Aquí entra tu SmartSet)
        if (rawValue instanceof java.util.Set<?> set) {
            if (set instanceof SmartSet) return set;
            return new SmartSet<>(set);
        }

        // 3. MAPAS
        if (rawValue instanceof java.util.Map<?,?> map) {
            if (map instanceof SmartMap) return map;
            return new SmartMap<>(map);
        }

        // Arrays y otros tipos se devuelven tal cual
        return rawValue;
    }
    
    /**
     * Calcula un hash basado en el contenido de los campos del objeto (Reflexión).
     * Esto permite detectar cambios en POJOs mutables que no implementan hashCode().
     */
    private int getPojoHash(Object o) {
        if (o == null) return 0;
        
        // Si es un tipo simple, usamos su hash normal
        if (o instanceof String || o instanceof Number || o instanceof Boolean) {
            return o.hashCode();
        }

        // Si es una colección o mapa, usamos su hash estándar
        if (o instanceof Collection || o instanceof Map) {
            return o.hashCode();
        }

        // Si es un POJO, recorremos sus campos
        int result = 1;
        try {
            for (Field f : o.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object val = f.get(o);
                int elementHash = (val == null) ? 0 : val.hashCode();
                result = 31 * result + elementHash;
            }
        } catch (Exception e) {
            // Si falla la reflexión, fallback al hash de identidad
            return o.hashCode();
        }
        return result;
    }

}