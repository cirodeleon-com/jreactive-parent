package com.ciro.jreactive.ast;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ComponentBlueprintCompiler {

    private static final Pattern MUSTACHE_PATTERN = Pattern.compile("\\{\\{\\s*([^}]+?)\\s*}}");

    private ComponentBlueprintCompiler() {
    }

    public interface ComponentResolver {
        ResolvedComponent resolve(String tagName, String ownerPackage);
    }

    public static final class ResolvedComponent {
        private final String tagName;
        private final String packageName;
        private final String template;

        public ResolvedComponent(String tagName, String packageName, String template) {
            this.tagName = tagName;
            this.packageName = packageName;
            this.template = template;
        }

        public String tagName() {
            return tagName;
        }

        public String packageName() {
            return packageName;
        }

        public String template() {
            return template;
        }
    }

    private static final class BindingValue {
        private final String value;
        private final boolean literal;

        private BindingValue(String value, boolean literal) {
            this.value = value == null ? "" : value;
            this.literal = literal;
        }

        public String value() {
            return value;
        }

        public boolean literal() {
            return literal;
        }
    }

    public static String compile(String rawHtml, ComponentResolver resolver, String ownerPackage) {
        if (rawHtml == null || rawHtml.isBlank()) {
            return "";
        }
        List<JrxNode> nodes = JrxParser.parse(rawHtml);
        StringBuilder out = new StringBuilder();
        renderNodes(nodes, out, resolver, ownerPackage, new LinkedHashMap<>(), new LinkedHashSet<>());
        return out.toString();
    }

    private static void renderNodes(
            List<JrxNode> nodes,
            StringBuilder out,
            ComponentResolver resolver,
            String ownerPackage,
            Map<String, BindingValue> bindings,
            Set<String> aliases
    ) {
        for (JrxNode node : nodes) {
            renderNode(node, out, resolver, ownerPackage, bindings, aliases);
        }
    }

    private static void renderNode(
            JrxNode node,
            StringBuilder out,
            ComponentResolver resolver,
            String ownerPackage,
            Map<String, BindingValue> bindings,
            Set<String> aliases
    ) {
        if (node == null) {
            return;
        }

        if (node instanceof ComponentNode comp) {
            renderComponent(comp, out, resolver, ownerPackage, bindings, aliases);
            return;
        }

        if (node instanceof IfNode ifNode) {
            renderIf(ifNode, out, resolver, ownerPackage, bindings, aliases);
            return;
        }

        if (node instanceof EachNode eachNode) {
            renderEach(eachNode, out, resolver, ownerPackage, bindings, aliases);
            return;
        }

        if (node instanceof ElementNode el) {
            renderElement(el, out, resolver, ownerPackage, bindings, aliases);
            return;
        }

        if (node instanceof TextNode txt) {
            out.append(rewriteText(txt.text, bindings, aliases));
            return;
        }

        node.renderRaw(out);
    }

    private static void renderComponent(
            ComponentNode comp, StringBuilder out, ComponentResolver resolver,
            String ownerPackage, Map<String, BindingValue> parentBindings, Set<String> parentAliases) {
        
        ResolvedComponent resolved = resolver.resolve(comp.tagName, ownerPackage);
        if (resolved == null || resolved.template() == null || resolved.template().isBlank()) {
            renderComponentFallback(comp, out, resolver, ownerPackage, parentBindings, parentAliases);
            return;
        }

        Map<String, String> compiledSlots = new HashMap<>();
        List<JrxNode> defaultNodes = new ArrayList<>();
        for (JrxNode c : comp.children) {
            if (c instanceof ElementNode e && "template".equalsIgnoreCase(e.tagName) && e.attributes.containsKey("slot")) {
                compiledSlots.put(e.attributes.get("slot"), compileSlot(e.children, resolver, ownerPackage, parentBindings, parentAliases));
            } else {
                defaultNodes.add(c);
            }
        }
        compiledSlots.put("default", compileSlot(defaultNodes, resolver, ownerPackage, parentBindings, parentAliases));

        Map<String, BindingValue> childBindings = buildChildBindings(comp.attributes, parentBindings, parentAliases);
        for (Map.Entry<String, String> entry : compiledSlots.entrySet()) {
            childBindings.put("slot_" + entry.getKey(), new BindingValue(entry.getValue(), true));
        }

        List<JrxNode> childNodes = JrxParser.parse(resolved.template());
        renderNodes(childNodes, out, resolver, resolved.packageName(), childBindings, new LinkedHashSet<>());
    }

    private static void renderComponentFallback(
            ComponentNode comp,
            StringBuilder out,
            ComponentResolver resolver,
            String ownerPackage,
            Map<String, BindingValue> bindings,
            Set<String> aliases
    ) {
        out.append("<").append(comp.tagName);

        for (Map.Entry<String, String> attr : comp.attributes.entrySet()) {
            String key = normalizeAttrName(attr.getKey());
            String value = rewriteAttributeValue(key, attr.getValue(), bindings, aliases);
            writeAttr(out, key, value);
        }

        if (comp.isSelfClosing) {
            out.append("/>");
            return;
        }

        out.append(">");
        for (JrxNode child : comp.children) {
            renderNode(child, out, resolver, ownerPackage, bindings, aliases);
        }
        out.append("</").append(comp.tagName).append(">");
    }

    private static void renderIf(
            IfNode ifNode,
            StringBuilder out,
            ComponentResolver resolver,
            String ownerPackage,
            Map<String, BindingValue> bindings,
            Set<String> aliases
    ) {
        Boolean baked = tryBakeLiteralIf(ifNode.condition, bindings);

        if (baked != null) {
            if (baked) {
                renderNodes(ifNode.trueBranch, out, resolver, ownerPackage, bindings, aliases);
            } else {
                renderNodes(ifNode.falseBranch, out, resolver, ownerPackage, bindings, aliases);
            }
            return;
        }

        String condition = rewriteExpression(ifNode.condition, bindings, aliases);

        out.append("<template data-if=\"").append(escapeAttr(condition)).append("\">");
        renderNodes(ifNode.trueBranch, out, resolver, ownerPackage, bindings, aliases);
        out.append("</template>");

        if (!ifNode.falseBranch.isEmpty()) {
            out.append("<template data-else=\"").append(escapeAttr(condition)).append("\">");
            renderNodes(ifNode.falseBranch, out, resolver, ownerPackage, bindings, aliases);
            out.append("</template>");
        }
    }
    
    private static Boolean tryBakeLiteralIf(String expr, Map<String, BindingValue> bindings) {
        if (expr == null) return null;

        String trimmed = expr.trim();
        if (trimmed.isEmpty()) return Boolean.FALSE;

        // Solo horneamos casos simples tipo:
        // title
        // onSubmit
        // required
        // helpText
        // checked
        if (!trimmed.matches("[a-zA-Z_][\\w.]*")) {
            return null;
        }

        String root = extractRoot(trimmed);
        if (root == null) return null;

        BindingValue binding = bindings.get(root);
        if (binding == null) return null;

        // Solo se puede hornear si viene como literal del padre.
        // Ej: title="hola", onSubmit="register(form)", required="true"
        if (!binding.literal()) {
            return null;
        }

        // Si la condición es algo como "field.algo" pero field es literal,
        // no intentamos navegarla; solo horneamos el caso simple exacto.
        if (!trimmed.equals(root)) {
            return null;
        }

        return isTruthyLiteral(binding.value());
    }

    private static boolean isTruthyLiteral(String raw) {
        if (raw == null) return false;

        String v = raw.trim();
        if (v.isEmpty()) return false;

        if ("false".equalsIgnoreCase(v)) return false;
        if ("0".equals(v)) return false;
        if ("null".equalsIgnoreCase(v)) return false;
        if ("undefined".equalsIgnoreCase(v)) return false;

        return true;
    }

    private static void renderEach(
            EachNode eachNode,
            StringBuilder out,
            ComponentResolver resolver,
            String ownerPackage,
            Map<String, BindingValue> bindings,
            Set<String> aliases
    ) {
        String listExpr = rewriteExpression(eachNode.listExpression, bindings, aliases);

        out.append("<template data-each=\"")
           .append(escapeAttr(listExpr))
           .append(":")
           .append(escapeAttr(eachNode.alias))
           .append("\">");

        Set<String> childAliases = new LinkedHashSet<>(aliases);
        childAliases.add(eachNode.alias);

        renderNodes(eachNode.children, out, resolver, ownerPackage, bindings, childAliases);
        out.append("</template>");
    }

    private static void renderElement(
            ElementNode el,
            StringBuilder out,
            ComponentResolver resolver,
            String ownerPackage,
            Map<String, BindingValue> bindings,
            Set<String> aliases
    ) {
    	if ("slot".equalsIgnoreCase(el.tagName)) {
            String slotName = el.attributes.getOrDefault("name", "default");
            BindingValue slot = bindings.get("slot_" + slotName);
            if (slot != null) out.append(slot.value());
            return;
        }

        out.append("<").append(el.tagName);

        for (Map.Entry<String, String> attr : el.attributes.entrySet()) {
            String key = normalizeAttrName(attr.getKey());
            String value = rewriteAttributeValue(key, attr.getValue(), bindings, aliases);
            writeAttr(out, key, value);
        }

        if (el.isSelfClosing) {
            out.append("/>");
            return;
        }

        out.append(">");
        renderNodes(el.children, out, resolver, ownerPackage, bindings, aliases);
        out.append("</").append(el.tagName).append(">");
    }

    private static String compileSlot(
            List<JrxNode> slotNodes,
            ComponentResolver resolver,
            String ownerPackage,
            Map<String, BindingValue> bindings,
            Set<String> aliases
    ) {
        if (slotNodes == null || slotNodes.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        renderNodes(slotNodes, sb, resolver, ownerPackage, bindings, aliases);
        return sb.toString();
    }

    private static Map<String, BindingValue> buildChildBindings(
            Map<String, String> attrs,
            Map<String, BindingValue> parentBindings,
            Set<String> parentAliases
    ) {
        Map<String, BindingValue> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : attrs.entrySet()) {
            String rawKey = e.getKey();
            String normalizedKey = normalizeAttrName(rawKey);
            String rawValue = e.getValue() == null ? "" : e.getValue();

            boolean literal =
                    !rawKey.startsWith(":")
                    && !rawKey.startsWith("@")
                    && !"data-call".equals(rawKey)
                    && !rawKey.startsWith("client:");

            String finalValue;
            if (literal) {
                finalValue = rawValue;
            } else {
                finalValue = rewriteExpression(rawValue, parentBindings, parentAliases);
            }

            result.put(normalizedKey, new BindingValue(finalValue, literal));
        }
        return result;
    }

    private static String normalizeAttrName(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        if (key.startsWith(":")) {
            return key.substring(1);
        }
        return key;
    }

    private static String rewriteAttributeValue(
            String key,
            String value,
            Map<String, BindingValue> bindings,
            Set<String> aliases
    ) {
        String raw = value == null ? "" : value;

        if (raw.contains("{{")) {
            String rewritten = rewriteText(raw, bindings, aliases);
            if (isControlOrEventAttr(key)) {
                return unwrapSingleMustache(rewritten);
            }
            return rewritten;
        }

        if (isControlOrEventAttr(key)) {
            return rewriteExpression(raw, bindings, aliases);
        }

        return raw;
    }

    private static boolean isControlOrEventAttr(String key) {
        return key.startsWith("@")
                || "data-call".equals(key)
                || "data-if".equals(key)
                || "data-else".equals(key)
                || "data-each".equals(key)
                || "name".equals(key) // 🔥 FIX: Para que los inputs funcionen
                || "id".equals(key)
                || key.startsWith("client:");
    }

    private static String rewriteText(
            String text,
            Map<String, BindingValue> bindings,
            Set<String> aliases
    ) {
        if (text == null || text.isEmpty() || !text.contains("{{")) {
            return text == null ? "" : text;
        }

        Matcher m = MUSTACHE_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (m.find()) {
            String expr = m.group(1).trim();
            String replacement = rewriteMustacheExpression(expr, bindings, aliases);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String rewriteMustacheExpression(
            String expr,
            Map<String, BindingValue> bindings,
            Set<String> aliases
    ) {
        String root = extractRoot(expr);
        BindingValue binding = root == null ? null : bindings.get(root);

        if (binding != null && binding.literal() && expr.equals(root)) {
            return binding.value();
        }

        return "{{" + rewriteExpression(expr, bindings, aliases) + "}}";
    }

    private static String rewriteExpression(
            String expr,
            Map<String, BindingValue> bindings,
            Set<String> aliases
    ) {
        if (expr == null || expr.isBlank()) {
            return "";
        }

        String rewritten = expr;
        List<Map.Entry<String, BindingValue>> entries = new ArrayList<>(bindings.entrySet());
        entries.sort(Comparator.comparingInt((Map.Entry<String, BindingValue> e) -> e.getKey().length()).reversed());

        for (Map.Entry<String, BindingValue> e : entries) {
            String key = e.getKey();
            BindingValue binding = e.getValue();

            if (aliases.contains(key) || binding.literal()) {
                continue;
            }

            rewritten = rewritten.replaceAll(
                    "(?<![\\w$.])" + Pattern.quote(key) + "(?=\\b|\\.)",
                    Matcher.quoteReplacement(binding.value())
            );
        }

        return rewritten;
    }

    private static String extractRoot(String expr) {
        if (expr == null || expr.isBlank()) {
            return null;
        }
        int i = 0;
        while (i < expr.length()) {
            char c = expr.charAt(i);
            if (Character.isLetter(c) || c == '_') {
                break;
            }
            i++;
        }
        if (i >= expr.length()) {
            return null;
        }
        int j = i + 1;
        while (j < expr.length()) {
            char c = expr.charAt(j);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                break;
            }
            j++;
        }
        return expr.substring(i, j);
    }

    private static String unwrapSingleMustache(String value) {
        if (value == null) {
            return "";
        }
        Matcher m = MUSTACHE_PATTERN.matcher(value.trim());
        if (m.matches()) {
            return m.group(1).trim();
        }
        return value;
    }

    private static void writeAttr(StringBuilder out, String key, String value) {
        if (key == null || key.isBlank()) {
            return;
        }
        out.append(" ").append(key);
        if (value != null && !value.isEmpty()) {
            out.append("=\"").append(escapeAttr(value)).append("\"");
        }
    }

    private static String escapeAttr(String s) {
        if (s == null) {
            return "";
        }
        return s
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}