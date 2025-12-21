package com.ciro.jreactive;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import com.ciro.jreactive.factory.ComponentFactory;
import com.ciro.jreactive.factory.DefaultComponentFactory;
import java.util.Objects;



public abstract class AbstractComponentEngine implements ComponentEngine.Strategy {

    protected static final Pattern IF_BLOCK = Pattern.compile("\\{\\{\\s*#if\\s+([^}]+)}}([\\s\\S]*?)\\{\\{\\s*/if}}", Pattern.MULTILINE);
    protected static final Pattern IF_ELSE_BLOCK = Pattern.compile("\\{\\{\\s*#if\\s+([^}]+)}}([\\s\\S]*?)\\{\\{\\s*else}}([\\s\\S]*?)\\{\\{\\s*/if}}", Pattern.MULTILINE);
    protected static final Pattern EACH_BLOCK = Pattern.compile("\\{\\{\\s*#each\\s+([\\w#.-]+)(?:\\s+as\\s+(\\w+))?\\s*\\}\\}([\\s\\S]*?)\\{\\{\\s*/each\\s*\\}\\}", Pattern.MULTILINE);
    
    private static volatile ComponentFactory componentFactory = new DefaultComponentFactory();

    public static void setComponentFactory(ComponentFactory factory) {
        componentFactory = Objects.requireNonNull(factory, "componentFactory must not be null");
    }


    protected String processControlBlocks(String html) {
        html = IF_ELSE_BLOCK.matcher(html).replaceAll("<template data-if=\"$1\">$2</template><template data-else=\"$1\">$3</template>");
        html = IF_BLOCK.matcher(html).replaceAll("<template data-if=\"$1\">$2</template>");
        
        Matcher m2 = EACH_BLOCK.matcher(html);
        StringBuffer sb = new StringBuffer();
        while (m2.find()) {
            String listExpr = m2.group(1).trim();
            String alias = (m2.group(2) != null) ? m2.group(2).trim() : "this";
            String tpl = String.format("<template data-each=\"%s:%s\">%s</template>", listExpr, alias, m2.group(3));
            m2.appendReplacement(sb, Matcher.quoteReplacement(tpl));
        }
        m2.appendTail(sb);
        return sb.toString();
    }

    protected String renderChildLogic(
            HtmlComponent ctx,
            List<HtmlComponent> pool,
            Map<String, ReactiveVar<?>> all,
            String className,
            Map<String, String> attrMap, 
            String slotHtml
    ) {
        try {
            String refAlias = attrMap.get("ref");

            /* ── A) Instancia / reutiliza ── */
            ViewLeaf leaf;
            if (refAlias != null) {
                leaf = pool.stream().filter(c -> refAlias.equals(c.getId())).map(c -> (ViewLeaf) c).findFirst()
                        .orElseGet(() -> {
                            ViewLeaf f = newInstance(ctx, className);
                            f.setId(refAlias);
                            return f;
                        });
                pool.removeIf(c -> c == leaf);
            } else {
                Optional<HtmlComponent> reused = pool.stream()
                        .filter(c -> c.getClass().getSimpleName().equals(className)).findFirst();
                if (reused.isPresent()) {
                    leaf = (ViewLeaf) reused.get();
                    pool.remove(reused.get());
                    leaf.setId(leaf.getId());
                } else {
                    leaf = newInstance(ctx, className);
                    leaf.setId(leaf.getId());
                }
            }

            if (leaf instanceof HtmlComponent hc) {
                if (slotHtml != null && !slotHtml.isBlank()) hc._setSlotHtml(slotHtml);
                ctx._addChild(hc);
            }

            /* ── B) Props y Bindings ── */
            final Map<String, ReactiveVar<?>> allRx = all;
            if (leaf instanceof HtmlComponent hc) {
                Map<String, ReactiveVar<?>> childBinds = hc.selfBindings();
                
                attrMap.forEach((key, val) -> {
                    if (key.equals("ref")) return;
                    
                    boolean binding = key.startsWith(":");
                    String prop = binding ? key.substring(1) : key;
                    
                    @SuppressWarnings("unchecked")
                    var target = (ReactiveVar<Object>) childBinds.get(prop);
                    if (target == null) return;

                    ReactiveVar<?> parentRx = null;
                    if (binding) {
                        parentRx = ctx.selfBindings().get(val);
                        if (parentRx == null) parentRx = allRx.get(val);
                    }
                    Object value = (binding && parentRx != null) ? parentRx.get() : val;
                    target.set(value);

                    if (binding && parentRx != null) {
                        parentRx.onChange(x -> target.set(x));
                    }
                });
            }

            /* ── C) Namespacing y Render del Hijo ── */
            String ns = leaf.getId() + ".";
            String child = leaf.render();

            // 1. Reescribir variables
            for (String key : leaf.bindings().keySet()) {
                String esc = Pattern.quote(key);
                child = child.replaceAll("\\{\\{\\s*" + esc + "([^}]*)}}", "{{" + ns + key + "$1}}");
                
                // 🔥 MEJORA: Acepta comillas simples o dobles [\"']
                child = child.replaceAll("name\\s*=\\s*[\"']" + esc + "[\"']", "name=\"" + ns + key + "\"");
                child = child.replaceAll("data-if\\s*=\\s*[\"']" + esc + "[\"']", "data-if=\"" + ns + key + "\"");
                
                // Para data-each es delicado por el ":"
                // Jsoup puede haber formateado data-each="key:alias". 
                // Buscamos la key seguida de :
                child = child.replaceAll("data-each\\s*=\\s*([\"'])" + esc + ":", "data-each=$1" + ns + key + ":");
                
                child = child.replaceAll("data-param\\s*=\\s*[\"']" + esc + "[\"']", "data-param=\"" + ns + key + "\"");
            }

            // 2. Interpolación estática
            for (var e : leaf.bindings().entrySet()) {
                String key = e.getKey();
                //String val = String.valueOf(e.getValue().get() == null ? "" : e.getValue().get());
                Object rawVal = e.getValue().get();
                String val = (rawVal == null) ? "" : HtmlEscaper.escape(String.valueOf(rawVal));
                
                String expr = "{{" + ns + key + "}}";
                
                // Acepta comillas simples o dobles
                child = child.replaceAll("=\\s*[\"']\\s*" + Pattern.quote(expr) + "\\s*[\"']", "=\"" + Matcher.quoteReplacement(val) + "\"");
                child = child.replaceAll(">\\s*" + Pattern.quote(expr) + "\\s*<", ">" + Matcher.quoteReplacement(val) + "<");
            }

            // 3. Eliminar ref
            if (refAlias != null) {
                child = child.replaceFirst("\\s+ref\\s*=\\s*[\"']" + Pattern.quote(refAlias) + "[\"']", "");
            }

            // 4. Namespacing de Eventos
            // Regex mejorada para aceptar comillas simples o dobles en los eventos
            Pattern evtPat = Pattern.compile("@(?<evt>click|submit|change|input)\\s*=\\s*([\"'])(?<method>[\\w#.-]+)\\((?<args>[^)]*)\\)\\2");
            Matcher evtM = evtPat.matcher(child);
            StringBuffer sbEvt = new StringBuffer();
            while (evtM.find()) {
                String evt = evtM.group("evt");
                String method = evtM.group("method");
                String args = evtM.group("args").trim();
                
                boolean handledHere = Arrays.stream(leaf.getClass().getMethods())
                    .anyMatch(m -> m.isAnnotationPresent(com.ciro.jreactive.annotations.Call.class) && m.getName().equals(method));

                if (!handledHere) {
                    evtM.appendReplacement(sbEvt, Matcher.quoteReplacement(evtM.group(0)));
                    continue;
                }
                String namespacedArgs = Arrays.stream(args.split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).map(a -> ns + a).collect(Collectors.joining(","));
                
                String replacement = String.format("@%s=\"%s.%s(%s)\"", evt, ns.substring(0, ns.length() - 1), method, namespacedArgs);
                evtM.appendReplacement(sbEvt, Matcher.quoteReplacement(replacement));
            }
            evtM.appendTail(sbEvt);
            child = sbEvt.toString();
            
         // 🔥 FIX FINAL: eliminar restos de control blocks no evaluados
            child = child.replaceAll("\\{\\{\\s*#if[^}]*}}", "");
            child = child.replaceAll("\\{\\{\\s*/if\\s*}}", "");
            child = child.replaceAll("\\{\\{\\s*else\\s*}}", "");


            // 5. Validar duplicados y guardar bindings
            if (refAlias != null) {
                boolean dup = all.keySet().stream().anyMatch(k -> k.startsWith(ns));
                if (dup) throw new IllegalStateException("Duplicate ref alias '" + refAlias + "'");
            }
            leaf.bindings().forEach((k, v) -> all.put(ns + k, v));

            return child;

        } catch (Exception ex) {
            throw new RuntimeException("Error rendering child " + className, ex);
        }
    }

    private ViewLeaf newInstance(HtmlComponent ctx, String className) {
        try {
            Class<?> raw = Class.forName(ctx.getClass().getPackageName() + "." + className);
            if (!ViewLeaf.class.isAssignableFrom(raw)) {
                throw new IllegalStateException("Tag <" + className + "> is not a ViewLeaf: " + raw.getName());
            }
            @SuppressWarnings("unchecked")
            Class<? extends ViewLeaf> type = (Class<? extends ViewLeaf>) raw;

            return (ViewLeaf) componentFactory.create(type);

        } catch (RuntimeException re) {
            throw re;
        } catch (Exception ex) {
            throw new RuntimeException("Error creating child: " + className, ex);
        }
    }

    
    /**
     * Limpia los componentes que no se reutilizaron en este renderizado.
     * Esto previene fugas de memoria (zombies) y memory leaks de listeners.
     */
    protected void disposeUnused(List<HtmlComponent> pool) {
        if (pool == null || pool.isEmpty()) return;
        
        for (HtmlComponent zombie : pool) {
            zombie._unmountRecursive(); // 🔥 Libera listeners y recursos
        }
        pool.clear();
    }
}