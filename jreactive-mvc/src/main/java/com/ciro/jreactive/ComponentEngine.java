package com.ciro.jreactive;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resuelve etiquetas <Componente/> y convierte {{#if}} / {{#each}}. */
final class ComponentEngine {

    /*─────────────────────────── 1. PATTERNS ───────────────────────────*/
    private static final Pattern TAG =
        Pattern.compile("<\\s*([A-Z][A-Za-z0-9_]*)\\s*/>", Pattern.MULTILINE);

    private static final Pattern IF_BLOCK =
        Pattern.compile("\\{\\{#if\\s+([^}]+)}}([\\s\\S]*?)\\{\\{/if}}",
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
                ViewLeaf leaf = (ViewLeaf) Class
                    .forName(ctx.getClass().getPackageName() + "." + className)
                    .getDeclaredConstructor()
                    .newInstance();
                
                if (leaf instanceof HtmlComponent && ctx instanceof HtmlComponent) {
                    HtmlComponent hc     = (HtmlComponent) leaf;
                    HtmlComponent parent = (HtmlComponent) ctx;
                    parent._addChild(hc);
                }


                String ns = leaf.getId() + ".";
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
                        .replaceAll("data-each\\s*=\\s*\"" + esc + "\"",
                                    "data-each=\"" + ns + key + "\"");
                }
                out.append(child);
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

        html = IF_BLOCK.matcher(html)
                .replaceAll("<template data-if=\"$1\">$2</template>");

        /* 2-D) CONVERSIÓN {{#each key [as alias]}} → <template data-each="key:alias"> */
        Matcher m2 = EACH_BLOCK.matcher(html);
        StringBuffer sb = new StringBuffer();
        while (m2.find()) {
            String listKey = m2.group(1);                      // ej. "fruits"
            String alias   = m2.group(2) != null 
                             ? m2.group(2)                    // ej. "fruit"
                             : "this";                       // fallback
            String body    = m2.group(3);                      // contenido interno
            String tpl     = String.format(
                "<template data-each=\"%s:%s\">%s</template>",
                listKey, alias, body
            );
            m2.appendReplacement(sb, Matcher.quoteReplacement(tpl));
        }
        m2.appendTail(sb);
        html = sb.toString();

        return new Rendered(html, all);

    }

    private ComponentEngine() {}   // util-class
}
