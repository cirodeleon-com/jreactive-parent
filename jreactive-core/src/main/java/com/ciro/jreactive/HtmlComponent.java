package com.ciro.jreactive;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class HtmlComponent extends ViewLeaf {

    private Map<String, ReactiveVar<?>> map;      // se crea on-demand
    private ComponentEngine.Rendered cached;
    private final List<HtmlComponent> _children = new ArrayList<>();
    void _addChild(HtmlComponent child) { _children.add(child); }
    List<HtmlComponent> _children()     { return _children; }
     // para IDs automáticos
    


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
}


