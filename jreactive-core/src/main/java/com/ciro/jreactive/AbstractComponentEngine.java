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

        // ---------------------------------------------------------
        // 1. GENERACIÓN DETERMINISTA DE ID (La Solución)
        // ---------------------------------------------------------
        // No importa si tiene ref o no. Siempre calculamos el ID basado 
        // en el orden (secuencia). Así, si el servidor reinicia, 
        // el primer JModal siempre será "Page-JModal-0".
        int seq = parent._nextChildIdSeq(className);
        String stableId = parent.getId() + "-" + className + "-" + seq;

        // 2. BUSCAR EN EL POOL (Reciclaje)
        // Buscamos si existe un componente con este ID exacto
        Optional<HtmlComponent> match = pool.stream()
                .filter(c -> c.getId().equals(stableId))
                .findFirst();

        if (match.isPresent()) {
            // ¡Lo encontramos! (Caso Stateful o mismo render)
            leaf = (ViewLeaf) match.get();
            pool.remove(leaf); 
        } else {
            // No existe (Caso Stateless / Reinicio / Primera vez)
            // Creamos uno nuevo y le FORZAMOS el ID estable.
            leaf = newInstance(parent, className);
            leaf.setId(stableId); 
        }
        
        HtmlComponent hc = (HtmlComponent) leaf;
        if (slotHtml != null && !slotHtml.isBlank()) hc._setSlotHtml(slotHtml);
        
        // 3. REGISTRO DE ALIAS (Para que findChild funcione)
        // Aunque el ID sea "Page-Modal-0", guardamos que "clientModal" apunta a él.
        if (ref != null && !ref.isBlank()) {
            String simpleRef = ref;
            if (ref.contains(parent.getId() + ".")) {
                simpleRef = ref.replace(parent.getId() + ".", "");
            }
            parent._registerRef(simpleRef, hc.getId());
        }
        
        parent._addChild(hc);
        
        // 4. BINDINGS (Tu lógica original intacta)
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
                        String shortKey = v.substring(v.indexOf('.') + 1);
                        pRx = parent.getRawBindings().get(shortKey);
                        if (pRx == null) pRx = globalBindings.get(shortKey);
                    }
                    
                    if (pRx == null && v.startsWith(parent.getId() + ".")) {
                        String shortKey = v.substring(parent.getId().length() + 1);
                        pRx = parent.getRawBindings().get(shortKey);
                    }
                    
                    // Tu Fix Robusto para hermanos
                    if (pRx == null && v.contains(".")) {
                        String refKey = v.substring(0, v.indexOf('.')); // "hello"
                        String varName = v.substring(v.indexOf('.') + 1); // "newFruit"

                        // A. Traducir el alias ("hello" -> "HomePage-HelloLeaf-0")
                        String resolvedId = parent._resolveRef(refKey);

                        for (HtmlComponent sibling : parent._children()) {
                            // 1. Coincidencia por Alias Resuelto (Lo nuevo)
                            if (resolvedId != null && sibling.getId().equals(resolvedId)) {
                                pRx = sibling.getRawBindings().get(varName);
                                if (pRx != null) break;
                            }
                            
                            // 2. Coincidencia directa (Legacy o si usaron el ID largo)
                            if (sibling.getId().equals(refKey)) {
                                pRx = sibling.getRawBindings().get(varName);
                                if (pRx != null) break;
                            }
                            
                            // 3. Tu lógica anterior (Por si el nombre de variable incluye ID)
                            if (v.startsWith(sibling.getId() + ".")) {
                                String sub = v.substring(sibling.getId().length() + 1);
                                pRx = sibling.getRawBindings().get(sub);
                                if (pRx != null) break;
                            }
                        }
                    }

                    if (pRx != null) {
                        target.set(pRx.get());
                        pRx.onChange(target::set);
                    } else {
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
                // 1. Intento rápido: mismo paquete que el padre (ej: com.ciro.jreactive.crud.JButton)
                String localPackageClass = ctx.getClass().getPackageName() + "." + className;
                raw = Class.forName(localPackageClass);
            } catch (ClassNotFoundException e) {
                try {
                    // 2. Fallback: Buscar por nombre global (si el template tiene el nombre completo)
                    raw = Class.forName(className);
                } catch (ClassNotFoundException e2) {
                    // 🔥 3. FIX: Buscar en el paquete base de componentes UI (com.ciro.jreactive)
                    // Esto permite que <JButton> funcione desde cualquier subpaquete
                    String uiPackageClass = "com.ciro.jreactive." + className;
                    raw = Class.forName(uiPackageClass);
                }
            }
            return (ViewLeaf) componentFactory.create((Class<? extends ViewLeaf>) raw);
        } catch (Exception e) { 
            throw new RuntimeException("Error: No se encontró el componente '" + className + 
                "'. Verifica el nombre, imports o usa el paquete completo (ej: com.app.ui." + className + ")", e); 
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