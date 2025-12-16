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

public abstract class HtmlComponent extends ViewLeaf {

    private Map<String, ReactiveVar<?>> map;      // se crea on-demand
    private ComponentEngine.Rendered cached;
    private final List<HtmlComponent> _children = new ArrayList<>();
    
    // [Nuevo] Rastreador de keys de estado para auto-sync
    private final Set<String> stateKeys = new HashSet<>(); 

    private final AtomicReference<ComponentState> _state =
            new AtomicReference<>(ComponentState.UNMOUNTED);
    
    /** Contenido raw que va entre <MiComp> ... </MiComp> */
    private String slotHtml = "";

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
                        // @State siempre envuelve el valor tal cual en un ReactiveVar
                        ReactiveVar<Object> srx = new ReactiveVar<>(raw);
                        srx.setActiveGuard(() -> _state() == ComponentState.MOUNTED);

                        String key = stateAnn.value().isBlank() ? f.getName() : stateAnn.value();
                        map.put(key, srx);
                        
                        // [Nuevo] Guardamos la key para sincronización automática
                        stateKeys.add(key);
                    }

                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
            c = c.getSuperclass();
        }
    }

    // [Nuevo] Método mágico para sincronizar estado post-ejecución
    public void _syncState() {
        if (map == null) buildBindings();

        for (String key : stateKeys) {
            try {
                @SuppressWarnings("unchecked")
                ReactiveVar<Object> rx = (ReactiveVar<Object>) map.get(key);
                // Obtenemos valor fresco por reflection
                Object freshValue = getFieldValueByName(key); 
                if (rx != null) {
                    rx.set(freshValue); // Dispara actualización al frontend
                }
            } catch (Exception e) {
                System.err.println("Error auto-syncing state '" + key + "': " + e.getMessage());
            }
        }
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

}