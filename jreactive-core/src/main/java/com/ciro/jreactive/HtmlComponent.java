package com.ciro.jreactive;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public abstract class HtmlComponent extends ViewLeaf {

    private Map<String, ReactiveVar<?>> map;      // se crea on-demand
    private ComponentEngine.Rendered cached;
    private final List<HtmlComponent> _children = new ArrayList<>();
    private final AtomicReference<ComponentState> _state =
            new AtomicReference<>(ComponentState.UNMOUNTED);
    
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
        for (Field f : getClass().getDeclaredFields()) {
            Bind ann = f.getAnnotation(Bind.class);
            if (ann == null) continue;

            f.setAccessible(true);
            try {
                Object raw = f.get(this);

                ReactiveVar<?> rx =
                        (raw instanceof ReactiveVar<?> r) ? r :
                        (raw instanceof Type<?> v)        ? v.rx() :
                                                            new ReactiveVar<>(raw);
                
                rx.setActiveGuard(() -> _state() == ComponentState.MOUNTED);

                String key = ann.value().isBlank() ? f.getName() : ann.value();
                map.put(key, rx);

            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
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

}


