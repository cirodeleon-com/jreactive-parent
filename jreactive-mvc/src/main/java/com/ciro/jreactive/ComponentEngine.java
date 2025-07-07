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

    private static final Pattern EACH_BLOCK =
        Pattern.compile("\\{\\{#each\\s+([^}]+)}}([\\s\\S]*?)\\{\\{/each}}",
                        Pattern.MULTILINE);

    private static long COUNTER = 0;                    // ClockLeaf#1., …

    record Rendered(String html, Map<String,ReactiveVar<?>> bindings) {}

    /*─────────────────────────── 2. RENDER ────────────────────────────*/
    static Rendered render(HtmlComponent ctx) {

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

        html = EACH_BLOCK.matcher(html)
                .replaceAll("<template data-each=\"$1\">$2</template>");

        return new Rendered(html, all);
    }

    private ComponentEngine() {}   // util-class
}
