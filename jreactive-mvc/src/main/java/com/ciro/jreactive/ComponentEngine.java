package com.ciro.jreactive;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.Optional;


/** Resuelve etiquetas <Componente/> y convierte {{#if}} / {{#each}}. */
final class ComponentEngine {

    /*─────────────────────────── 1. PATTERNS ───────────────────────────*/
    //private static final Pattern TAG =
    //    Pattern.compile("<\\s*([A-Z][A-Za-z0-9_]*)\\s*/>", Pattern.MULTILINE);
    
 // Sustituye la línea que tenías por esta
    private static final Pattern TAG =
        Pattern.compile("<\\s*([A-Z][A-Za-z0-9_]*)([^/>]*)/>", Pattern.MULTILINE);


    private static final Pattern IF_BLOCK =
        Pattern.compile("\\{\\{#if\\s+([^}]+)}}([\\s\\S]*?)\\{\\{/if}}",
                        Pattern.MULTILINE);
    
    /** {{#if cond}}true{{else}}false{{/if}} */
    private static final Pattern IF_ELSE_BLOCK =
        Pattern.compile(
          "\\{\\{#if\\s+([^}]+)}}([\\s\\S]*?)\\{\\{else}}([\\s\\S]*?)\\{\\{/if}}",
          Pattern.MULTILINE);


 // 1) Captura opcionalmente "as alias"
 // 1) Captura opcional “as alias”
    /** Captura '{{#each key [as alias]}}...{{/each}}' */
    /** Captura '{{#each key [as alias]}}...{{/each}}', permitiendo espacios antes de '}}' */
    private static final Pattern EACH_BLOCK =
        Pattern.compile(
          "\\{\\{#each\\s+([\\w#.-]+)(?:\\s+as\\s+(\\w+))?\\s*\\}\\}([\\s\\S]*?)\\{\\{\\/each\\s*\\}\\}",
          Pattern.MULTILINE
        );





    private static long COUNTER = 0;                    // ClockLeaf#1., …

    record Rendered(String html, Map<String,ReactiveVar<?>> bindings) {}

    /*─────────────────────────── 2. RENDER ────────────────────────────*/
    static Rendered render(HtmlComponent ctx) {
    	
    	ctx._children().clear(); 

        /* 2-A) Procesar subcomponentes <ClockLeaf/> … -----------------*/
        StringBuilder out = new StringBuilder();
        Map<String,ReactiveVar<?>> all = new HashMap<>();

        Matcher m = TAG.matcher(ctx.template());
        int cursor = 0;

        while (m.find()) {
            out.append(ctx.template(), cursor, m.start());

            try {
            	String className = m.group(1);
            	
            	// ─── Captura de atributos del tag  (ej.  ref="hello"  :greet="expr") ───
            	String rawAttrs = m.group(2);                 // texto “ crudo ” entre el nombre y "/>"
            	Map<String,String> attrMap = parseProps(rawAttrs);
            	String refAlias  = attrMap.get("ref");        // null si no existe


            	/* ── A) Instancia / reutiliza según ref ───────────────────────────── */
            	ViewLeaf leaf;

            	if (refAlias != null) {                       // ① hay ref  ⇒ busca por id
            	    leaf = ctx._children().stream()
            	             .filter(c -> refAlias.equals(c.getId()))
            	             .map(c -> (ViewLeaf) c)
            	             .findFirst()
            	             .orElseGet(() -> {               // no estaba  ⇒ crear + fijar id
            	                 ViewLeaf fresh = newInstance(ctx, className);
            	                 fresh.setId(refAlias);       // id estable = alias
            	                 if (fresh instanceof HtmlComponent hc) ctx._addChild(hc);
            	                 return fresh;
            	             });

            	} else {                                      // ② sin ref ⇒ SIEMPRE nueva
            	    leaf = newInstance(ctx, className);
            	    if (leaf instanceof HtmlComponent hc) ctx._addChild(hc);
            	}


            	/* añade al listado de hijos si aún no estaba */
            	if (leaf instanceof HtmlComponent hc && !ctx._children().contains(hc)) {
            	    ctx._addChild(hc);
            	}


                
            	/* -----------------------------------------------------------
            	 *  A)  Props  (literales y bindings de 1 nivel)
            	 *      – ahora busca también en 'all' y deja el puente reactivo
            	 * ----------------------------------------------------------- */
            	Map<String,String> rawProps = parseProps(m.group(2));      // atributos del tag
            	final Map<String,ReactiveVar<?>> allRx = all;              // acceso dentro lambda

            	if (leaf instanceof HtmlComponent hc) {
            	    Map<String,ReactiveVar<?>> childBinds = hc.selfBindings(); // asegura mapa

            	    rawProps.forEach((attr, val) -> {
            	        boolean binding = attr.startsWith(":");         // :greet="expr"
            	        String  prop    = binding ? attr.substring(1)   // greet
            	                                  : attr;               // greet

            	        @SuppressWarnings("unchecked")
            	        var target = (ReactiveVar<Object>) childBinds.get(prop);
            	        if (target == null) return;                     // el hijo no declara @Bind

            	        ReactiveVar<?> parentRx = null;
            	        if (binding) {
            	            /* ❶ intenta primero en los @Bind propios, luego en los hijos ya renderizados */
            	            parentRx = ctx.selfBindings().get(val);
            	            if (parentRx == null) parentRx = allRx.get(val);
            	        }

            	        Object value = (binding && parentRx != null) ? parentRx.get() : val;
            	        target.set(value);

            	        /* ❷ puente reactivo: si cambia el padre, actualiza el hijo */
            	        if (binding && parentRx != null) {
            	            parentRx.onChange(x -> target.set(x));
            	        }
            	    });
            	}
            	/* ───────────── FIN BLOQUE PROPS ───────────── */




                //String ns = leaf.getId() + ".";
            	String ns = (refAlias != null ? refAlias : leaf.getId()) + ".";

                System.out.println("🔗 Renderizando componente con namespace: " + ns);


                

                String child = leaf.render();           // HTML del hijo

                /* Prefix para {{var}}, name="var", data-if/each="var" */
                for (String key : leaf.bindings().keySet()) {
                    String esc = Pattern.quote(key);
                    child = child
                        .replaceAll("\\{\\{\\s*" + esc + "\\s*}}",
                                    "{{" + ns + key + "}}")
                        .replaceAll("name\\s*=\\s*\"" + esc + "\"",
                                    "name=\"" + ns + key + "\"")
                        .replaceAll("data-if\\s*=\\s*\"" + esc + "\"",
                                    "data-if=\"" + ns + key + "\"")
                        .replaceAll("data-each\\s*=\\s*\"" + esc + ":",
                                    "data-each=\"" + ns + key + ":")
                        .replaceAll("data-param\\s*=\\s*\"" + esc + "\"", "data-param=\"" + ns + key + "\"")
                        ;
                }
                
                if (refAlias != null) {
                    // elimina  ref="alias"  solo en la PRIMERA ocurrencia del hijo
                    child = child.replaceFirst("\\s+ref=\""+Pattern.quote(refAlias)+"\"", "");
                }

                
                
                

                Pattern clickPat = Pattern.compile("@click=\"(\\w+)\\(([^)]*)\\)\"");
                Matcher clickM = clickPat.matcher(child);
                StringBuffer sbClick = new StringBuffer();
                while (clickM.find()) {
                    String method = clickM.group(1);
                    String args   = clickM.group(2).trim();
                    // split on comma, prefix each with ns, rejoin
                    String namespacedArgs = Arrays.stream(args.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(a -> ns + a)
                        .collect(Collectors.joining(","));
                    String replacement = "@click=\"" + ns + method + "(" + namespacedArgs + ")\"";
                    clickM.appendReplacement(sbClick, Matcher.quoteReplacement(replacement));
                }
                clickM.appendTail(sbClick);
                child = sbClick.toString();

                
                
                
                out.append(child);
                
                if (refAlias != null) {
                    boolean dup = all.keySet().stream().anyMatch(k -> k.startsWith(ns));
                    if (dup)
                        throw new IllegalStateException("Duplicate ref alias '"+refAlias+"' inside parent component");
                }

                
                leaf.bindings().forEach((k,v)-> all.put(ns + k, v));

            } catch (Exception ex) {
                throw new RuntimeException("Error instanciando componente", ex);
            }
            cursor = m.end();
        }
        out.append(ctx.template(), cursor, ctx.template().length());

        /* 2-B) Bindings propios del componente padre -----------------*/
        all.putAll(ctx.selfBindings());

        /* 2-C) CONVERSIÓN {{#if}} / {{#each}} → <template …> ---------*/
        String html = out.toString();
        
        html = IF_ELSE_BLOCK.matcher(out.toString())
        	      .replaceAll("<template data-if=\"$1\">$2</template><template data-else=\"$1\">$3</template>");


        html = IF_BLOCK.matcher(html)
                .replaceAll("<template data-if=\"$1\">$2</template>");

        /* 2-D) CONVERSIÓN {{#each key [as alias]}} → <template data-each="key:alias"> */

     // 2-D) CONVERSIÓN {{#each key [as alias]}} → <template data-each="key:alias">
        Matcher m2 = EACH_BLOCK.matcher(html);
        StringBuffer sb = new StringBuffer();
        while (m2.find()) {
            String rawKey = m2.group(1);            // "fruits"
            String alias  = m2.group(2) != null
                            ? m2.group(2)
                            : "this";              // "fruit"
            String body   = m2.group(3);

            // 1) Intentamos extraer el namespace inspeccionando los {{alias}} del body
            Pattern pAlias = Pattern.compile("\\{\\{\\s*([\\w#.-]+)\\." + alias + "\\s*\\}\\}");
            Matcher mAlias = pAlias.matcher(body);

            String listKey;
            if (mAlias.find()) {
                // ej. mAlias.group(1) == "hello" o "HelloLeaf#1"
                listKey = mAlias.group(1) + "." + rawKey;
            } else {
                // fallback — en caso de que no encontremos alias (poco probable)
                listKey = all.keySet().stream()
                             .filter(k -> k.endsWith("." + rawKey))
                             .findAny()
                             .orElse(rawKey);
            }

            String tpl = String.format(
                "<template data-each=\"%s:%s\">%s</template>",
                listKey, alias, body
            );
            m2.appendReplacement(sb, Matcher.quoteReplacement(tpl));
        }
        m2.appendTail(sb);
        html = sb.toString();
        
        String rootNs = ctx.getId() + ".";

     // aplica namespacing a TODOS los bindings en 'all'
     for (String fullKey : all.keySet()) {
         // el nombre simple es la parte después del último '.'
         String simple = fullKey.substring(fullKey.lastIndexOf('.') + 1);
         String esc    = Pattern.quote(simple);

         // name="foo" → name="ComponentId.foo"
         html = html.replaceAll(
           "\\bname\\s*=\\s*\"" + esc + "\"",
           "name=\"" + rootNs + fullKey + "\""
         );

         // data-param="foo" → data-param="ComponentId.foo"
         html = html.replaceAll(
           "\\bdata-param\\s*=\\s*\"" + esc + "\"",
           "data-param=\"" + rootNs + fullKey + "\""
         );

         // ref="foo" → ref="ComponentId.foo"
         html = html.replaceAll(
           "\\bref\\s*=\\s*\"" + esc + "\"",
           "ref=\"" + rootNs + fullKey + "\""
         );
     }


        return new Rendered(html, all);

    }
    
    /** Convierte  foo="bar"  y  :foo="expr"  →  Map */
    private static Map<String,String> parseProps(String raw) {
        Map<String,String> map = new HashMap<>();
        if (raw == null) return map;

        Matcher mm = Pattern.compile("(\\:?\\w+)\\s*=\\s*\"([^\"]*)\"").matcher(raw);
        while (mm.find()) {
            map.put(mm.group(1), mm.group(2));
        }
        return map;
    }
    
    /* ╭──────────────────────────────────────────────────────────────╮
     * │   Crea un componente por reflexión                           │
     * ╰──────────────────────────────────────────────────────────────╯ */
    private static ViewLeaf newInstance(HtmlComponent ctx, String className) {
        try {
            return (ViewLeaf) Class
                .forName(ctx.getClass().getPackageName() + "." + className)
                .getDeclaredConstructor()
                .newInstance();
        } catch (Exception ex) {
            throw new RuntimeException("Error instanciando componente", ex);
        }
    }



    private ComponentEngine() {}   // util-class
}
