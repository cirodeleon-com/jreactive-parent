package com.ciro.jreactive;

import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.parser.Parser;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class JsoupComponentEngine extends AbstractComponentEngine {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{\\s*([\\w#.-]+)\\s*}}");
    private static final Pattern EVENT_PATTERN = Pattern.compile("^([\\w#.-]+)(?:\\((.*)\\))?$");
    private static final Pattern HTML5_VOID_FIX = 
        Pattern.compile("<(area|base|br|col|embed|hr|img|input|link|meta|param|source|track|wbr)([^>]*?)(?<!/)>", Pattern.CASE_INSENSITIVE);

    public static void installAsDefault() {
        ComponentEngine.setStrategy(new JsoupComponentEngine());
    }

    @Override
    public ComponentEngine.Rendered render(HtmlComponent ctx) {
        List<HtmlComponent> pool = new ArrayList<>(ctx._children());
        ctx._children().clear();
        Map<String, ReactiveVar<?>> all = new HashMap<>();

        // 1. Pre-procesamiento de bloques (con el fix de anidamiento)
        String rawTemplate = ctx.template();
        String htmlWithControlBlocks = processControlBlocks(rawTemplate);
        
        String xmlFriendly = HTML5_VOID_FIX.matcher(htmlWithControlBlocks).replaceAll("<$1$2/>");

        Document doc = Jsoup.parse(xmlFriendly, "", Parser.xmlParser());
        doc.outputSettings().prettyPrint(false).syntax(Document.OutputSettings.Syntax.html);

        // 2. Procesar Árbol
        processNodeTree(doc, ctx, pool, all, ""); 

        all.putAll(ctx.selfBindings());

        String html = doc.html();
        if (doc.children().size() == 1 && (doc.child(0).tagName().equals("#root") || doc.child(0).tagName().equals("html"))) {
             html = doc.body().html(); 
        }

        disposeUnused(pool);
        ctx._mountRecursive();

        return new ComponentEngine.Rendered(html, all);
    }

    private void processNodeTree(Node node, HtmlComponent ctx, List<HtmlComponent> pool, Map<String, ReactiveVar<?>> all, String namespacePrefix) {
        List<Node> children = new ArrayList<>(node.childNodes());

        for (Node child : children) {
            if (child instanceof Element el) {
                if (isComponent(el)) {
                	handleChildComponent(el, ctx, pool, all, namespacePrefix);
                } else {
                    processElementAttributes(el, ctx, namespacePrefix);
                    processNodeTree(child, ctx, pool, all, namespacePrefix);
                }
            } else if (child instanceof TextNode textNode) {
                // Solo namespacing para texto (el JS hace el resto)
                processTextNode(textNode, ctx, namespacePrefix);
            }
        }
    }

    
    private void handleChildComponent(Element el, HtmlComponent ctx, List<HtmlComponent> pool, Map<String, ReactiveVar<?>> all, String namespacePrefix) {
        String className = el.tagName();
        Map<String, String> attrs = new HashMap<>();
        el.attributes().forEach(a -> attrs.put(a.getKey(), a.getValue()));
        
        // 1. Extraer slot y aplicar namespace del padre (ctx)
        String rawSlot = el.html();
        String namespacedSlot = namespaceString(rawSlot, namespacePrefix, ctx);

        // 2. CREAR E INSTANCIAR 
        // createAndBindComponent ya gestiona el parentRx.onChange(...) interno
        HtmlComponent childComp = createAndBindComponent(ctx, pool, all, className, attrs, namespacedSlot);
        
        String childId = childComp.getId();
        String childNs = childId + ".";

        // 3. Procesar plantilla del hijo
        String childRawTpl = childComp.template();
        String childProcessedTpl = processControlBlocks(childRawTpl);
        String childXml = HTML5_VOID_FIX.matcher(childProcessedTpl).replaceAll("<$1$2/>");

        // 4. PARSEAR E INYECTAR RECURSIVAMENTE
        // IMPORTANTE: Ahora pasamos el childComp y su propio childNs
        List<Node> childNodes = Parser.parseXmlFragment(childXml, "");
        for (Node n : childNodes) {
            processNodeTree(n, childComp, new ArrayList<>(), all, childNs); 
        }

        // 5. Reemplazar el tag <JButton> o <JSelect> por el contenido renderizado
        Node prev = el;
        for (Node n : childNodes) {
            prev.after(n);
            prev = n;
        }
        el.remove();
        
        // Registrar bindings del hijo en el mapa global para que el JS los vea
        childComp.bindings().forEach((k, v) -> all.put(childNs + k, v));
    }
    
    private void processElementAttributes(Element el, HtmlComponent ctx, String ns) {
        // Hacemos una copia de la lista para iterar sin problemas de concurrencia
        List<Attribute> attrs = new ArrayList<>(el.attributes().asList());

        for (Attribute attr : attrs) {
            String key = attr.getKey();
            String val = attr.getValue(); // Valor original (ej: "{{onSubmit}}")

            // 1. FIX: Renombrar 'name' para bindings de inputs
            //    Esto permite que el framework enlace el input con el modelo
            if (key.equals("name") && !val.contains("{{")) {
                String root = val.split("\\.")[0];
                if (ctx.selfBindings().containsKey(root)) {
                    el.attr(key, ns + val);
                }
                continue; 
            }

            // 2. Procesar variables {{...}} y Directivas :prop
            //    Aquí "{{onSubmit}}" se convierte en "register(form)"
            if (val.contains("{{") || key.startsWith(":")) {
                String namespacedVal = namespaceString(val, ns, ctx);
                el.attr(key, namespacedVal); 
                // injectStaticValue puede cambiar el valor del atributo en 'el'
                injectStaticValue(el, key, namespacedVal, ctx, ns);
            }

            // 🔥 CORRECCIÓN MAESTRA: 
            // Leemos el valor ACTUAL del elemento, no el 'val' original.
            // Si el paso 2 cambió el valor, aquí lo atrapamos.
            String currentVal = el.hasAttr(key) ? el.attr(key) : val;

            // 3. Eventos: @click, @submit, data-call
            //    Ahora rewriteEvent recibe "register(form)", que es válido, 
            //    en lugar de "{{onSubmit}}", que era inválido.
            if (key.startsWith("@") || key.equals("data-call")) {
               el.attr(key, rewriteEvent(currentVal, ns, ctx));
            }

            // 4. Directivas de control (data-if, data-else)
            if (key.equals("data-if") || key.equals("data-else")) {
                el.attr(key, namespaceExpression(val, ns, ctx));
            }
            
            // 5. Data-each
            if (key.equals("data-each")) {
                String[] parts = val.split(":");
                if (parts.length > 0) {
                    String listExpr = parts[0];
                    String rest = parts.length > 1 ? ":" + parts[1] : "";
                    el.attr(key, namespaceExpression(listExpr, ns, ctx) + rest);
                }
            }
        }
    }

    private void processTextNode(TextNode node, HtmlComponent ctx, String ns) {
        String text = node.getWholeText();
        if (!text.contains("{{")) return;
        // Solo namespace, NO inyección de valor en texto (para no romper hidratación JS)
        node.text(namespaceString(text, ns, ctx));
    }

    private String namespaceString(String input, String ns, HtmlComponent ctx) {
        if (ns.isEmpty()) return input; 
        Matcher m = VAR_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String varName = m.group(1);
            String root = varName.split("\\.")[0];
            
            boolean isKnownBinding = ctx.selfBindings().containsKey(root);

            if (varName.equals("this") || varName.startsWith(ns) || !isKnownBinding) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement("{{" + ns + varName + "}}"));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String namespaceExpression(String expr, String ns, HtmlComponent ctx) {
        if (ns.isEmpty()) return expr;
        if (expr.equals("this")) return expr;
        String clean = expr.trim();
        if (clean.startsWith("!")) {
            return "!" + namespaceExpression(clean.substring(1), ns, ctx);
        }

        String root = clean.split("\\.")[0];
        boolean isKnownBinding = ctx.selfBindings().containsKey(root);

        if (!isKnownBinding) return clean;
        return ns + clean;
    }

    private String rewriteEvent(String callSignature, String ns, HtmlComponent ctx) {
        if (ns.isEmpty()) return callSignature;

        Matcher m = EVENT_PATTERN.matcher(callSignature.trim());
        if (!m.matches()) return callSignature;

        String methodName = m.group(1);
        String args = m.group(2);

        // Si ya tiene un prefijo de instancia, no tocar
        if (callSignature.contains("#")) return callSignature;

        // 🔥 FIX CLAVE: si el método NO es @Call en este componente, NO namespacing.
        // Esto permite que eventos en slots queden apuntando al padre.
        if (!hasCallable(ctx, methodName)) {
            return callSignature;
        }

        String newMethod = ns.substring(0, ns.length() - 1) + "." + methodName;

        if (args == null || args.isBlank()) return newMethod + "()";

        String newArgs = Arrays.stream(args.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(arg -> {
                if (arg.isEmpty()) return arg;
                if (arg.startsWith("'") || arg.startsWith("\"") ||
                    Character.isDigit(arg.charAt(0)) ||
                    arg.equals("true") || arg.equals("false")) return arg;

                String root = arg.split("\\.")[0];
                return ctx.selfBindings().containsKey(root) ? ns + arg : arg;
            })
            .collect(Collectors.joining(", "));

        return newMethod + "(" + newArgs + ")";
    }


    private void injectStaticValue(Element el, String attrKey, String namespacedVal, HtmlComponent ctx, String ns) {
        // Buffer mutable para ir haciendo los reemplazos
        String currentVal = namespacedVal;
        boolean changed = false;

        Matcher m = VAR_PATTERN.matcher(namespacedVal);
        
        // 🔄 1. Iterar sobre TODAS las variables encontradas ({{a}} {{b}})
        while (m.find()) {
            String token = m.group(0);      // "{{this.variant}}"
            String fullKey = m.group(1);    // "this.variant" o "JCard#1.variant"
            
            // 🧹 2. Limpiar el Namespace para obtener la clave local
            String localKey = fullKey;
            if (ns != null && !ns.isEmpty() && localKey.startsWith(ns)) {
                localKey = localKey.substring(ns.length()); // "variant"
            }
            
            // 🧹 3. Limpiar prefijo "this." (¡Esto faltaba!)
            if (localKey.startsWith("this.")) {
                localKey = localKey.substring(5); // "variant"
            }
            
            // 🔍 4. Buscar en el mapa
            ReactiveVar<?> var = ctx.selfBindings().get(localKey);
            if (var != null) {
                Object val = var.get();
                String strVal = (val == null) ? "" : HtmlEscaper.escape(String.valueOf(val));
                
                // Reemplazo literal (seguro contra $ y \)
                currentVal = currentVal.replace(token, strVal);
                changed = true;
            } else {
                // (Opcional) Log para saber si falla la búsqueda
                // System.out.println("⚠️ Binding no encontrado para estático: " + localKey);
            }
        }

        // 💾 5. Aplicar cambios al DOM
        if (changed) {
            // Si es una directiva :prop (ej: :disabled), usamos el nombre real
            if (attrKey.startsWith(":")) {
                String realKey = attrKey.substring(1); 
                el.attr(realKey, currentVal);
                el.removeAttr(attrKey); 
            } else {
                // Atributo normal (ej: class)
                el.attr(attrKey, currentVal);
            }
        } 
        // Caso especial: Atributo estático puro inyectado (ej: :required="true")
        else if (attrKey.startsWith(":")) {
            // Si no tenía variables {{}} pero era :algo, asumimos que el valor es literal
            el.attr(attrKey.substring(1), namespacedVal);
            el.removeAttr(attrKey);
        }
    }

    private boolean isComponent(Element el) {
        return !el.tagName().isEmpty() && Character.isUpperCase(el.tagName().charAt(0));
    }
    
    private boolean hasCallable(HtmlComponent ctx, String methodName) {
        return Arrays.stream(ctx.getClass().getMethods())
                .anyMatch(m ->
                        m.getName().equals(methodName) &&
                        m.isAnnotationPresent(com.ciro.jreactive.annotations.Call.class)
                );
    }

    
}