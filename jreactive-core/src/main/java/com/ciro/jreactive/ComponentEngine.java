package com.ciro.jreactive;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.Optional;

/** Resuelve etiquetas <Componente/> y convierte {{#if}} / {{#each}}. */
final class ComponentEngine {

    /*─────────────────────────── 1. PATTERNS ───────────────────────────*/

    // Captura el nombre del componente y todo lo que hay entre el nombre y "/>"
    // Ej: <JInput field="x" :label="Nombre" />
    private static final Pattern TAG =
        Pattern.compile("<\\s*([A-Z][A-Za-z0-9_]*)([^/>]*)/>", Pattern.MULTILINE);

    private static final Pattern IF_BLOCK =
        Pattern.compile("\\{\\{#if\\s+([^}]+)}}([\\s\\S]*?)\\{\\{/if}}",
                        Pattern.MULTILINE);

    /** {{#if cond}}true{{else}}false{{/if}} */
    private static final Pattern IF_ELSE_BLOCK =
        Pattern.compile(
          "\\{\\{#if\\s+([^}]+)}}([\\s\\S]*?)\\{\\{else}}([\\s\\S]*?)\\{\\{/if}}",
          Pattern.MULTILINE
        );

    /** Captura '{{#each key [as alias]}}...{{/each}}', permitiendo espacios antes de '}}' */
    private static final Pattern EACH_BLOCK =
        Pattern.compile(
          "\\{\\{#each\\s+([\\w#.-]+)(?:\\s+as\\s+(\\w+))?\\s*\\}\\}([\\s\\S]*?)\\{\\{\\/each\\s*\\}\\}",
          Pattern.MULTILINE
        );
    
 // Nuevo: <JForm ...> ... </JForm>
    private static final Pattern PAIR_TAG =
        Pattern.compile(
            "<\\s*([A-Z][A-Za-z0-9_]*)\\b([^>]*)>([\\s\\S]*?)</\\1>",
            Pattern.MULTILINE
        );

    



    private static long COUNTER = 0;                    // ClockLeaf#1., …

    record Rendered(String html, Map<String,ReactiveVar<?>> bindings) {}

    /*─────────────────────────── 2. RENDER ────────────────────────────*/
    static Rendered render(HtmlComponent ctx) {

        // Reutilización de hijos (para evitar re-instanciarlos siempre)
        List<HtmlComponent> pool = new ArrayList<>(ctx._children());
        ctx._children().clear();

        /* 2-A) Procesar subcomponentes <ClockLeaf/>, <JInput/>, etc. --- */
        StringBuilder out = new StringBuilder();
        Map<String,ReactiveVar<?>> all = new HashMap<>();

        // 1) Partimos del template original
        String template = ctx.template();

        // 1-A) Primero: componentes con apertura/cierre <JForm>...</JForm>
        Matcher pairM = PAIR_TAG.matcher(template);
        StringBuilder tmp = new StringBuilder();
        int cursor = 0;

        while (pairM.find()) {
            // copia lo que hay antes del componente
            tmp.append(template, cursor, pairM.start());

            String className = pairM.group(1); // JForm, JCard, etc.
            String rawAttrs  = pairM.group(2); // atributos del tag
            String slotHtml  = pairM.group(3); // contenido interno

            String childHtml = renderChildComponent(
                    ctx,
                    pool,
                    all,
                    className,
                    rawAttrs,
                    slotHtml       // 👈 aquí va el SLOT
            );

            tmp.append(childHtml);
            cursor = pairM.end();
        }
        // resto del template después del último tag emparejado
        tmp.append(template, cursor, template.length());

        // Template tras procesar <Comp> ... </Comp>
        String afterPairs = tmp.toString();

        // 1-B) Ahora los componentes autocontenidos <JInput />, etc.
        Matcher m = TAG.matcher(afterPairs);
        cursor = 0;

        while (m.find()) {
            out.append(afterPairs, cursor, m.start());

            String className = m.group(1);
            String rawAttrs  = m.group(2);

            String childHtml = renderChildComponent(
                    ctx,
                    pool,
                    all,
                    className,
                    rawAttrs,
                    null          // 👈 aquí NO hay slot (self-closing)
            );

            out.append(childHtml);
            cursor = m.end();
        }
        out.append(afterPairs, cursor, afterPairs.length());

        /* 2-B) Bindings propios del componente padre -----------------*/
        all.putAll(ctx.selfBindings());

        /* 2-C) Conversión {{#if}} / {{#each}} → <template …> ---------*/
        String html = out.toString();

        // Bloques {{#if ...}} ... {{else}} ... {{/if}}
        html = IF_ELSE_BLOCK.matcher(html)
                .replaceAll(
                    "<template data-if=\"$1\">$2</template>" +
                    "<template data-else=\"$1\">$3</template>"
                );

        // Bloques {{#if ...}} ... {{/if}} simples
        html = IF_BLOCK.matcher(html)
                .replaceAll("<template data-if=\"$1\">$2</template>");

        // Bloques {{#each key [as alias]}} → <template data-each="key:alias">
        Matcher m2 = EACH_BLOCK.matcher(html);
        StringBuffer sb = new StringBuffer();
        while (m2.find()) {
            String listExpr = m2.group(1).trim();
            String alias = (m2.group(2) != null)
                    ? m2.group(2).trim()
                    : "this";

            String body = m2.group(3);

            String tpl = String.format(
                "<template data-each=\"%s:%s\">%s</template>",
                listExpr, alias, body
            );
            m2.appendReplacement(sb, Matcher.quoteReplacement(tpl));
        }
        m2.appendTail(sb);
        html = sb.toString();

        Rendered rendered = new Rendered(html, all);

        // Monta recursivamente este componente y sus hijos
        ctx._mountRecursive();

        return rendered;
    }

    /** Convierte  foo="bar"  y  :foo="expr"  →  Map */
    private static Map<String,String> parseProps(String raw) {
        Map<String,String> map = new HashMap<>();
        if (raw == null) return map;

        // Soporta nombres con o sin ":" al inicio (ej. field, :field)
        Matcher mm = Pattern.compile("(\\:?\\w+)\\s*=\\s*\"([^\"]*)\"").matcher(raw);
        // Si en el futuro quieres soportar guiones: usar esta versión:
        // Matcher mm = Pattern.compile("(:?[A-Za-z_][A-Za-z0-9_\\-]*)\\s*=\\s*\"([^\"]*)\"").matcher(raw);

        while (mm.find()) {
            map.put(mm.group(1), mm.group(2));
        }
        return map;
    }
    
    private static String renderChildComponent(
            HtmlComponent ctx,
            List<HtmlComponent> pool,
            Map<String, ReactiveVar<?>> all,
            String className,
            String rawAttrs,
            String slotHtml
    ) {
        try {
            Map<String,String> attrMap = parseProps(rawAttrs);
            String refAlias  = attrMap.get("ref");        // null si no existe

         // 🔥 Capturamos un posible @submit/@click/@change/@input en el tag del componente
            String delegatedHandler = extractPrimaryHandler(rawAttrs);

            /* ── A) Instancia / reutiliza según ref ───────────────────── */
            ViewLeaf leaf;

            if (refAlias != null) {
                // Caso con ref explícito
                leaf = pool.stream()
                        .filter(c -> refAlias.equals(c.getId()))
                        .map(c -> (ViewLeaf) c)
                        .findFirst()
                        .orElseGet(() -> {
                            ViewLeaf f = newInstance(ctx, className);
                            f.setId(refAlias);
                            return f;
                        });

                pool.removeIf(c -> c == leaf);
            } else {
                // SIN ref → intenta reutilizar por clase
                Optional<HtmlComponent> reused = pool.stream()
                        .filter(c -> c.getClass().getSimpleName().equals(className))
                        .findFirst();

                if (reused.isPresent()) {
                    leaf = (ViewLeaf) reused.get();   // mantiene id
                    pool.remove(reused.get());
                    leaf.setId(leaf.getId());
                } else {
                    leaf = newInstance(ctx, className);   // primera vez
                    leaf.setId(leaf.getId());             // congela id actual
                }
            }

            // Si es HtmlComponent, lo colgamos como hijo y le pasamos el slot
            if (leaf instanceof HtmlComponent hc) {
                if (slotHtml != null) {
                    hc._setSlotHtml(slotHtml);
                }
                ctx._addChild(hc);
            }

            /* ── B) Props: literales y bindings de 1 nivel ───────────── */
            Map<String,String> rawProps = parseProps(rawAttrs);
            final Map<String,ReactiveVar<?>> allRx = all;

            if (leaf instanceof HtmlComponent hc) {
                Map<String,ReactiveVar<?>> childBinds = hc.selfBindings(); // asegura mapa
                
               // 🔥 Si el tag tenía @click="algo" y el hijo tiene un @Bind "submit",
               //              usamos ese valor para que {{#if submit}} sea true y para el @click interno.
              if (delegatedHandler != null) {
                 @SuppressWarnings("unchecked")
                 ReactiveVar<Object> submitRx = (ReactiveVar<Object>) childBinds.get("submit");
                 if (submitRx != null) {
                     submitRx.set(delegatedHandler);   // ahora {{submit}} tiene "register(form)"
                 }
             }


                rawProps.forEach((attr, val) -> {
                    boolean binding = attr.startsWith(":");   // :greet="expr"
                    String  prop    = binding ? attr.substring(1)
                            : attr;

                    @SuppressWarnings("unchecked")
                    var target = (ReactiveVar<Object>) childBinds.get(prop);
                    if (target == null) return;               // el hijo no declara @Bind

                    ReactiveVar<?> parentRx = null;
                    if (binding) {
                        // intenta primero en los @Bind propios del padre
                        parentRx = ctx.selfBindings().get(val);
                        if (parentRx == null) parentRx = allRx.get(val);
                    }

                    Object value = (binding && parentRx != null) ? parentRx.get() : val;
                    target.set(value);

                    // puente reactivo: si cambia el padre, actualiza el hijo
                    if (binding && parentRx != null) {
                        parentRx.onChange(x -> target.set(x));
                    }
                });
            }

            /* ── C) Render del hijo + namespacing ────────────────────── */
            String ns = leaf.getId() + ".";
            System.out.println("🔗 Renderizando componente con namespace: " + ns);

            String child = leaf.render();           // HTML del hijo

            // Prefix para {{var}}, name="var", data-if/each="var", data-param="var"
            for (String key : leaf.bindings().keySet()) {
                String esc = Pattern.quote(key);

                // 1️⃣  {{ key }}  y  {{ key.algo.loquesea }}
                child = child.replaceAll(
                        "\\{\\{\\s*" + esc + "([^}]*)}}",
                        "{{" + ns + key + "$1}}"
                );

                // 2️⃣ name="key"
                child = child.replaceAll(
                        "name\\s*=\\s*\"" + esc + "\"",
                        "name=\"" + ns + key + "\""
                );

                // 3️⃣ data-if="key"
                child = child.replaceAll(
                        "data-if\\s*=\\s*\"" + esc + "\"",
                        "data-if=\"" + ns + key + "\""
                );

                // 4️⃣ data-each="key:alias"
                child = child.replaceAll(
                        "data-each\\s*=\\s*\"" + esc + ":",
                        "data-each=\"" + ns + key + ":"
                );

                // 5️⃣ data-param="key"
                child = child.replaceAll(
                        "data-param\\s*=\\s*\"" + esc + "\"",
                        "data-param=\"" + ns + key + "\""
                );
            }

            /* ── D) Interpolación de atributos estáticos ----------------- */
            for (var e : leaf.bindings().entrySet()) {
                String key        = e.getKey();
                ReactiveVar<?> rx = e.getValue();
                Object valObj     = rx.get();
                String val        = (valObj == null) ? "" : String.valueOf(valObj);

                String expr    = "{{" + ns + key + "}}";
                String pattern = "=\"\\s*" + Pattern.quote(expr) + "\\s*\"";
                String replace = "=\"" + Matcher.quoteReplacement(val) + "\"";

                child = child.replaceAll(pattern, replace);
            }

            // --- D2) Interpolación de texto puro: <option>{{ns.key}}</option> ---
            for (var e : leaf.bindings().entrySet()) {
                String key        = e.getKey();
                ReactiveVar<?> rx = e.getValue();
                Object valObj     = rx.get();
                String val        = (valObj == null) ? "" : String.valueOf(valObj);

                String expr    = "{{" + ns + key + "}}";

                // 1) Atributos que son exactamente la expresión
                String attrPattern = "=\"\\s*" + Pattern.quote(expr) + "\\s*\"";
                String attrReplace = "=\"" + Matcher.quoteReplacement(val) + "\"";
                child = child.replaceAll(attrPattern, attrReplace);

                // 2) Nodos de texto cuyo contenido ES solo la expresión
                String textPattern = ">\\s*" + Pattern.quote(expr) + "\\s*<";
                String textReplace = ">" + Matcher.quoteReplacement(val) + "<";
                child = child.replaceAll(textPattern, textReplace);
            }

            /* ── E) Eliminar ref="alias" del HTML del hijo (solo 1ª vez) ─ */
            if (refAlias != null) {
                child = child.replaceFirst("\\s+ref=\""+Pattern.quote(refAlias)+"\"", "");
            }

            /* ── F) Namespacing de @event="method(args)" para todos los eventos ----------------- */
            Pattern evtPat = Pattern.compile("@(?<evt>click|submit|change|input)=[\"'](?<method>[\\w#.-]+)\\((?<args>[^)]*)\\)[\"']");
            Matcher evtM = evtPat.matcher(child);
            StringBuffer sbEvt = new StringBuffer();
            while (evtM.find()) {
                String evt     = evtM.group("evt");     // click / submit / change / input
                String method  = evtM.group("method");
                String args    = evtM.group("args").trim();

                // Namespacing de args: a → ns+a
                String namespacedArgs = Arrays.stream(args.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(a -> ns + a)
                        .collect(Collectors.joining(","));

                String replacement = String.format(
                        "@%s=\"%s.%s(%s)\"",
                        evt, ns.substring(0, ns.length()-1), method, namespacedArgs
                );
                evtM.appendReplacement(sbEvt, Matcher.quoteReplacement(replacement));
            }
            evtM.appendTail(sbEvt);
            child = sbEvt.toString();


            // 🔥 Reenvío del @click del padre al <button> del hijo
            if (delegatedHandler != null) {
                child = injectClickIntoRootButton(child, delegatedHandler);
            }

            // Comprobación de alias duplicado
            if (refAlias != null) {
                boolean dup = all.keySet().stream().anyMatch(k -> k.startsWith(ns));
                if (dup)
                    throw new IllegalStateException("Duplicate ref alias '"+refAlias+"' inside parent component");
            }

            // Acumula bindings del hijo namespaced
            leaf.bindings().forEach((k,v)-> all.put(ns + k, v));

            return child;

        } catch (Exception ex) {
            throw new RuntimeException("Error instanciando componente", ex);
        }
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

    
    /** 
     * Extrae el "handler principal" desde los atributos del tag del componente.
     *
     * Soporta:
     *   @submit="..."
     *   @click="..."
     *   @change="..."
     *   @input="..."
     *
     * Y aplica prioridad:
     *   submit > click > change > input
     */
    private static String extractPrimaryHandler(String raw) {
        if (raw == null) return null;

        String submit = null;
        String click  = null;
        String change = null;
        String input  = null;

        Matcher mm = Pattern.compile("@(submit|click|change|input)\\s*=\\s*\"([^\"]*)\"")
                            .matcher(raw);

        while (mm.find()) {
            String evt  = mm.group(1);
            String expr = mm.group(2);

            switch (evt) {
                case "submit" -> submit = expr;
                case "click"  -> click  = expr;
                case "change" -> change = expr;
                case "input"  -> input  = expr;
            }
        }

        if (submit != null && !submit.isBlank()) return submit;
        if (click  != null && !click.isBlank())  return click;
        if (change != null && !change.isBlank()) return change;
        if (input  != null && !input.isBlank())  return input;
        return null;
    }


    /** Inyecta (o sustituye) un @click="..." en el primer <button ...> del hijo */
    private static String injectClickIntoRootButton(String childHtml, String handler) {
        if (handler == null || handler.isBlank()) return childHtml;

        // 1) Quitar cualquier @click="..." que ya tenga el <button>
        String withoutExisting = childHtml.replaceFirst("@click=['\"][^'\"]*['\"]", "");

        // 2) Buscar el primer <button ...> e inyectar el nuevo @click
        return withoutExisting.replaceFirst(
            "<button(\\s*)",
            "<button$1 @click=\"" + handler + "\" "
        );
    }

    
    private ComponentEngine() {}   // util-class
    
    
}
