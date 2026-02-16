package com.ciro.jreactive.template;

import com.ciro.jreactive.HtmlComponent;
import com.ciro.jreactive.ReactiveVar;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public class TemplateContext {
    private final Map<String, Object> localVars;
    private final TemplateContext parent;
    private final HtmlComponent component; 

    public TemplateContext(HtmlComponent component) {
        this.component = component;
        this.localVars = new HashMap<>();
        this.parent = null;
    }

    private TemplateContext(TemplateContext parent, Map<String, Object> locals) {
        this.component = parent.component;
        this.localVars = locals;
        this.parent = parent;
    }

    public TemplateContext createChild(Map<String, Object> locals) {
        return new TemplateContext(this, locals);
    }

    public Object resolve(String path) {
        if (path == null || path.isBlank()) return null;
        if (path.equals("this")) return localVars.get("this");
        
        // Literales
        if (path.equals("true")) return true;
        if (path.equals("false")) return false;
        if (path.startsWith("'") && path.endsWith("'")) return path.substring(1, path.length()-1);
        if (isNumeric(path)) return Double.parseDouble(path);

        String[] parts = path.split("\\.");
        String root = parts[0];
        Object value = null;

        // 1. Buscar Raíz (Contexto Local -> Componente -> Padre)
        if (localVars.containsKey(root)) {
            value = localVars.get(root);
        } else if (component.getRawBindings().containsKey(root)) {
            ReactiveVar<?> rx = component.getRawBindings().get(root);
            value = (rx != null) ? rx.get() : null;
        } else if (parent != null) {
            return parent.resolve(path);
        }

        // 2. Navegación Profunda (ord -> address -> street)
        if (value != null && parts.length > 1) {
            for (int i = 1; i < parts.length; i++) {
                value = getProperty(value, parts[i]);
                if (value == null) break;
            }
        }
        
        // Helpers de tamaño
        if (parts.length > 1 && (parts[parts.length-1].equals("size") || parts[parts.length-1].equals("length"))) {
             if (value == null) return getSize(resolve(path.substring(0, path.lastIndexOf('.'))));
        }

        return value;
    }

    public boolean evaluate(String expr) {
        String clean = expr.trim();
        boolean negate = false;
        if (clean.startsWith("!")) {
            negate = true;
            clean = clean.substring(1).trim();
        }
        Object val = resolve(clean);
        return negate ? !isTruthy(val) : isTruthy(val);
    }

    private boolean isTruthy(Object o) {
        if (o == null) return false;
        if (o instanceof Boolean b) return b;
        if (o instanceof Collection<?> c) return !c.isEmpty();
        if (o instanceof String s) return !s.isEmpty();
        if (o instanceof Number n) return n.doubleValue() != 0;
        return true;
    }

    private int getSize(Object o) {
        if (o instanceof Collection<?> c) return c.size();
        if (o instanceof Map<?,?> m) return m.size();
        if (o instanceof String s) return s.length();
        return 0;
    }

    // ========================================================================
    // 🔥 EL MOTOR DE REFLEXIÓN (Soporta Records, Getters y Fields)
    // ========================================================================
    private Object getProperty(Object obj, String fieldName) {
        if (obj == null) return null;
        if (obj instanceof Map<?,?> m) return m.get(fieldName);
        
        Class<?> c = obj.getClass();
        try {
            // A. Intentar como MÉTODO (Indispensable para Records: street())
            Method m = findMethod(c, fieldName);
            if (m != null) {
                m.setAccessible(true);
                return m.invoke(obj);
            }

            // B. Intentar como CAMPO (Para clases normales)
            Field f = findField(c, fieldName);
            if (f != null) {
                f.setAccessible(true);
                return f.get(obj);
            }
        } catch (Exception e) {
            // Silencioso
        }
        return null;
    }
    
    private Field findField(Class<?> c, String name) {
        while (c != null && c != Object.class) {
            try { return c.getDeclaredField(name); } catch (Exception e) { c = c.getSuperclass(); }
        }
        return null;
    }

    private Method findMethod(Class<?> c, String name) {
        // 1. Nombre exacto (Records: "street()")
        try { return c.getMethod(name); } catch (Exception e) {}
        // 2. Estilo Bean: "getStreet()"
        String getter = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        try { return c.getMethod(getter); } catch (Exception e) {}
        // 3. Estilo Boolean: "isUrgent()"
        String isser = "is" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        try { return c.getMethod(isser); } catch (Exception e) {}
        return null;
    }
    
    private double parseDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(o)); } catch(Exception e) { return 0; }
    }

    private boolean isNumeric(String str) {
        return str != null && str.matches("-?\\d+(\\.\\d+)?");
    }
    
    public static boolean evalSimple(HtmlComponent comp, String expr) {
    	        if (expr == null || expr.isBlank()) return false;
    	
    	        // Creamos un contexto efímero para evaluar una sola expresión
    	        TemplateContext ctx = new TemplateContext(comp);
    	        return ctx.evaluate(expr);
    	    }

}