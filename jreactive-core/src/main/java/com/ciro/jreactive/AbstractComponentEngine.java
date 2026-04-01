package com.ciro.jreactive;

import com.ciro.jreactive.factory.ComponentFactory;
import com.ciro.jreactive.factory.DefaultComponentFactory;
import com.ciro.jreactive.template.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractComponentEngine implements ComponentEngine.Strategy {


    private static final Pattern EVENT_CALL_SIG =
            Pattern.compile("^\\s*([\\w#.-]+)\\s*(?:\\((.*)\\))?\\s*$");

    private static volatile ComponentFactory componentFactory = new DefaultComponentFactory();
    
    

    public static void setComponentFactory(ComponentFactory factory) {
        componentFactory = Objects.requireNonNull(factory, "componentFactory must not be null");
    }
    
    

    private static String qualifyEventPropIfNeeded(HtmlComponent parent, String prop, String raw) {
        if (raw == null) return null;

        // Solo para props estilo "onClick", "onSubmit", "onChange", etc.
        if (prop == null || prop.length() < 3 || !prop.startsWith("on")) return raw;

        String v = raw.trim();
        Matcher m = EVENT_CALL_SIG.matcher(v);
        if (!m.matches()) return raw;

        String method = m.group(1);
        String args   = m.group(2); // puede ser null

        // Ya está cualificado (tus ids reales contienen '#')
        if (method.contains("#")) return raw;

        // Solo cualificar si ese método existe en el parent como callable
        if (parent.getCallableMethods() == null || !parent.getCallableMethods().containsKey(method)) {
            return raw;
        }

        String qualified = parent.getId() + "." + method;
        return (args == null) ? qualified : qualified + "(" + args + ")";
    }
    
    private static Object coerceLiteral(String v) {
        if (v == null) return null;
        String s = v.trim();

        // Quitar comillas si vienen
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            s = s.substring(1, s.length() - 1);
        }

        // boolean
        if ("true".equalsIgnoreCase(s)) return true;
        if ("false".equalsIgnoreCase(s)) return false;

        // int
        if (s.matches("-?\\d+")) {
            try { return Integer.parseInt(s); } catch (Exception ignored) {}
        }

        // double
        if (s.matches("-?\\d+\\.\\d+")) {
            try { return Double.parseDouble(s); } catch (Exception ignored) {}
        }

        // default: string
        return s;
    }



    protected HtmlComponent createAndBindComponent(HtmlComponent parent, List<HtmlComponent> pool, Map<String, ReactiveVar<?>> globalBindings, String className, Map<String, String> attrs, Map<String, String> slots) {
        String ref = attrs.get("ref");
        ViewLeaf leaf;

        int seq = parent._nextChildIdSeq(className);
        String stableId = parent.getId() + "-" + className + "-" + seq;

        Optional<HtmlComponent> match = pool.stream().filter(c -> c.getId().equals(stableId)).findFirst();

        if (match.isPresent()) {
            leaf = (ViewLeaf) match.get();
            pool.remove(leaf); 
            ((HtmlComponent)leaf)._clearBindingCleanups();
        } else {
            leaf = newInstance(parent, className);
            leaf.setId(stableId); 
        }
        
        HtmlComponent hc = (HtmlComponent) leaf;
        hc._setSlots(slots);
        
        if (ref != null && !ref.isBlank()) {
            String simpleRef = ref;
            if (ref.contains(parent.getId() + ".")) simpleRef = ref.replace(parent.getId() + ".", "");
            parent._registerRef(simpleRef, hc.getId());
        }
        parent._addChild(hc);
        
        Map<String, ReactiveVar<?>> childBinds = hc.getRawBindings();
        @SuppressWarnings("rawtypes")
        com.ciro.jreactive.spi.ComponentAccessor acc = com.ciro.jreactive.spi.AccessorRegistry.get(hc.getClass());

        attrs.forEach((k, v) -> {
            if (k.equals("ref") || k.equals("expose")) return;
            
            boolean isB = k.startsWith(":");
            String prop = isB ? k.substring(1) : k;
            
            @SuppressWarnings("unchecked")
            ReactiveVar<Object> target = (ReactiveVar<Object>) childBinds.get(prop);
            
            ReactiveVar<?> pRx = null;

            if (isB) {
                pRx = parent.getRawBindings().get(v);
                if (pRx == null) pRx = globalBindings.get(v);
                
                if (pRx == null && v.contains(".")) {
                    String shortKey = v.substring(v.indexOf('.') + 1);
                    pRx = parent.getRawBindings().get(shortKey);
                    if (pRx == null) pRx = globalBindings.get(shortKey);
                }
                
                if (pRx == null && v.startsWith(parent.getId() + ".")) {
                    String shortKey = v.substring(parent.getId().length() + 1);
                    pRx = parent.getRawBindings().get(shortKey);
                }
                
                if (pRx == null && v.contains(".")) {
                    String refKey = v.substring(0, v.indexOf('.')); 
                    String varName = v.substring(v.indexOf('.') + 1); 
                    String resolvedId = parent._resolveRef(refKey);

                    for (HtmlComponent sibling : parent._children()) {
                        if (resolvedId != null && sibling.getId().equals(resolvedId)) {
                            pRx = sibling.getRawBindings().get(varName);
                            if (pRx != null) break;
                        }
                        if (sibling.getId().equals(refKey)) {
                            pRx = sibling.getRawBindings().get(varName);
                            if (pRx != null) break;
                        }
                        if (v.startsWith(sibling.getId() + ".")) {
                            String sub = v.substring(sibling.getId().length() + 1);
                            pRx = sibling.getRawBindings().get(sub);
                            if (pRx != null) break;
                        }
                    }
                }
            }

            // 🔥 APLICACIÓN DE DATOS DUAL (@Bind y @Prop)
            if (target != null) {
                // 1. Conexión de Reactividad
                if (isB) {
                    if (pRx != null) {
                        target.set(pRx.get());
                        Runnable unsub =pRx.onChange(target::set); 
                        hc._addBindingCleanup(unsub);
                    } else {
                        target.set(coerceLiteral(v));
                    }
                } else {
                    String fixed = qualifyEventPropIfNeeded(parent, prop, v);
                    target.set(fixed);
                }

                // 2. Inyección AOT Estática 
                if (acc != null) {
                    if (isB) {
                        Object parentValue = (pRx != null) ? pRx.get() : coerceLiteral(v);
                        acc.write(hc, prop, parentValue);
                    } else {
                        String fixed = qualifyEventPropIfNeeded(parent, prop, v);
                        acc.write(hc, prop, fixed);
                    }
                }
            }
        });
        
        return hc;
    }

    private ViewLeaf newInstance(HtmlComponent ctx, String className) {
        try {
            Class<?> raw = null;
            
            // 🔥 CIRUGÍA: Lista ordenada de paquetes donde el motor buscará la clase
            String[] packagesToTry = {
                ctx.getClass().getPackageName() + ".", // 1. Mismo paquete que el padre
                "",                                    // 2. Nombre global (si pasaste el FQCN en el HTML)
                "com.ciro.jreactive.",                 // 3. Paquete base UI (JTable, JButton, etc.)
                "com.ciro.jreactive.web.components.",  // 4. NUEVO: Paquete de Web Components
                "com.ciro.jreactive.components."       // 5. Fallback común para componentes extra
            };

            for (String prefix : packagesToTry) {
                try {
                    raw = Class.forName(prefix + className);
                    break; // ¡Lo encontramos! Salimos del bucle
                } catch (ClassNotFoundException ignored) {
                    // Ignoramos silenciosamente y probamos el siguiente paquete
                }
            }

            if (raw == null) {
                throw new ClassNotFoundException(className);
            }

            return (ViewLeaf) componentFactory.create((Class<? extends ViewLeaf>) raw);
            
        } catch (Exception e) { 
            throw new RuntimeException("Error: No se encontró el componente '" + className + 
                "'. Verifica el nombre, imports o usa el paquete completo (ej: com.app.ui." + className + ")", e); 
        }
    }
    
    
    @Override
    public String renderChild(HtmlComponent parent, String className, Map<String, String> attrs, Map<String, String> slots) {

        // ✅ Pool real (viene del _beginRenderCycle del parent)
        List<HtmlComponent> pool = parent._getRenderPool();
        if (pool == null) pool = new java.util.ArrayList<>(); // fallback defensivo

        HtmlComponent child = createAndBindComponent(
            parent,
            pool,
            parent.getRawBindings(), // ✅ NO llames parent.bindings() aquí (evita recursion/side effects)
            className,
            attrs,
            slots
        );

        child._initIfNeeded();
        child._syncState();
        child._mountRecursive();

        return child.render();
    }

    
    
    

    protected void disposeUnused(List<HtmlComponent> pool) {
        if (pool == null) return;
        for (HtmlComponent z : pool) z._unmountRecursive();
        pool.clear();
    }
}