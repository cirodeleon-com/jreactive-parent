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
    
    private final TemplateStrategy primary = new StackStrategy();
    private final TemplateStrategy fallback = new RegexStrategy();

    public static void setComponentFactory(ComponentFactory factory) {
        componentFactory = Objects.requireNonNull(factory, "componentFactory must not be null");
    }
    
    protected String processControlBlocks(String html, HtmlComponent ctx) {
        try {
            return primary.process(html, ctx);
        } catch (Exception e) {
            System.err.println("⚠️ [JReactive] Parser Robusto falló en " + ctx.getClass().getSimpleName() + 
                               ". Razón: " + e.getMessage());
            System.err.println("   🛡️ Usando Fallback Legacy (Regex)...");
            
            try {
                return fallback.process(html, ctx);
            } catch (Exception fatal) {
                // Si ambos fallan, devolvemos el HTML original para que se vea el error en el navegador o explote
                throw new RuntimeException("🔥 Error Fatal de Template: " + fatal.getMessage(), fatal);
            }
        }
    }

    protected String processControlBlocks(String html) {
        // Sin contexto no podemos usar el StackStrategy correctamente para resolver variables,
        // así que usamos el fallback directo o lanzamos error.
        // Asumiremos que siempre se llamará la versión con 'ctx'.
        return new RegexStrategy().process(html, null); 
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



    protected HtmlComponent createAndBindComponent(HtmlComponent parent, List<HtmlComponent> pool, Map<String, ReactiveVar<?>> globalBindings, String className, Map<String, String> attrs, String slotHtml) {
        String ref = attrs.get("ref");
        ViewLeaf leaf;
        if (ref != null) {
            leaf = pool.stream().filter(c -> ref.equals(c.getId())).map(c -> (ViewLeaf) c).findFirst()
                    .orElseGet(() -> { ViewLeaf f = newInstance(parent, className); f.setId(ref); return f; });
            pool.removeIf(c -> c == leaf);
        } else {
            Optional<HtmlComponent> re = pool.stream().filter(c -> c.getClass().getSimpleName().equals(className)).findFirst();
            if (re.isPresent()) { leaf = (ViewLeaf) re.get(); pool.remove(re.get()); }
            else leaf = newInstance(parent, className);
            
            //int index = parent._children().size(); 
            //String stableId = parent.getId() + "-" + className + "-" + index;
            
            //leaf.setId(stableId);
         // ✅ ID estable: si el componente ya existe (reuse), NO lo tocamos.
         // Solo asignamos ID cuando es NUEVO.
           // if (leaf.getId() == null || leaf.getId().isBlank()) {
                int seq = parent._nextChildIdSeq(className);
                String stableId = parent.getId() + "-" + className + "-" + seq;
                leaf.setId(stableId);
           // }

        }
        HtmlComponent hc = (HtmlComponent) leaf;
        if (slotHtml != null && !slotHtml.isBlank()) hc._setSlotHtml(slotHtml);
        parent._addChild(hc);
        
        Map<String, ReactiveVar<?>> childBinds = hc.getRawBindings();
        attrs.forEach((k, v) -> {
            if (k.equals("ref")) return;
            boolean isB = k.startsWith(":");
            String prop = isB ? k.substring(1) : k;
            ReactiveVar<Object> target = (ReactiveVar<Object>) childBinds.get(prop);
            if (target != null) {
            	if (isB) {
            	    ReactiveVar<?> pRx = parent.getRawBindings().get(v);
            	    if (pRx == null) pRx = globalBindings.get(v);
            	    
            	    if (pRx == null && v.contains(".")) {
                        // 🔥 FIX ROBUSTO: En lugar de cortar el string, iteramos los hermanos
                        // para ver si la variable "v" pertenece a alguno de ellos.
                        for (HtmlComponent sibling : parent._children()) {
                            String siblingId = sibling.getId();
                            
                            // Comprobamos si la variable empieza con el ID del hermano + punto
                            // Ej: v="page_1.hello.newFruit" empieza con id="page_1.hello" + "."
                            if (v.startsWith(siblingId + ".")) {
                                String subPath = v.substring(siblingId.length() + 1); // Extrae "newFruit"
                                pRx = sibling.getRawBindings().get(subPath);
                                
                                if (pRx != null) break; // ¡Encontrado! Dejamos de buscar
                            }
                        }
                    }

            	    if (pRx != null) {
            	        target.set(pRx.get());
            	        pRx.onChange(target::set);
            	    } else {
            	        // ✅ Fallback robusto: si no es binding real, es literal (ej: "form.country")
            	        target.set(coerceLiteral(v));
            	    }
            	} else {
            	    String fixed = qualifyEventPropIfNeeded(parent, prop, v);
            	    target.set(fixed);
            	}

            }
        });
        return hc;
    }

    private ViewLeaf newInstance(HtmlComponent ctx, String className) {
        try {
            Class<?> raw;
            try {
                // 1. Intento rápido: mismo paquete que el padre
                String localPackageClass = ctx.getClass().getPackageName() + "." + className;
                raw = Class.forName(localPackageClass);
            } catch (ClassNotFoundException e) {
                // 2. Fallback: Buscar por nombre global (reutilización entre paquetes)
                raw = Class.forName(className); 
            }
            return (ViewLeaf) componentFactory.create((Class<? extends ViewLeaf>) raw);
        } catch (Exception e) { 
            throw new RuntimeException("Error: No se encontró el componente '" + className + 
                "'. Verifica el nombre o usa el paquete completo (ej: com.app.ui." + className + ")", e); 
        }
    }
    
    
    @Override
    public String renderChild(HtmlComponent parent, String className, Map<String, String> attrs, String slot) {

        // ✅ Pool real (viene del _beginRenderCycle del parent)
        List<HtmlComponent> pool = parent._getRenderPool();
        if (pool == null) pool = new java.util.ArrayList<>(); // fallback defensivo

        HtmlComponent child = createAndBindComponent(
            parent,
            pool,
            parent.getRawBindings(), // ✅ NO llames parent.bindings() aquí (evita recursion/side effects)
            className,
            attrs,
            slot
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