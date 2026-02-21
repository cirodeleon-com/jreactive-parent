package com.ciro.jreactive.ast;

import com.ciro.jreactive.template.TemplateContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ElementNode implements JrxNode {
    public final String tagName;
    public final Map<String, String> attributes = new LinkedHashMap<>();
    public final List<JrxNode> children = new ArrayList<>();
    public final boolean isSelfClosing;

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{\\s*([\\w#.-]+)\\s*}}");

    public ElementNode(String tagName, boolean isSelfClosing) {
        this.tagName = tagName;
        this.isSelfClosing = isSelfClosing;
    }

    @Override
    public void renderRaw(StringBuilder sb) {
        if ("slot".equalsIgnoreCase(tagName)) {
            sb.append("<slot/>");
            return;
        }

        sb.append("<").append(tagName);
        for (Map.Entry<String, String> attr : attributes.entrySet()) {
            String key = attr.getKey();
            
            // 🔥 Quitar los ':' en el molde crudo para que el JS entienda los booleanos
            if (key.startsWith(":")) {
                key = key.substring(1);
            }
            
            sb.append(" ").append(key);
            if (attr.getValue() != null && !attr.getValue().isEmpty()) {
                sb.append("=\"").append(attr.getValue()).append("\"");
            }
        }
        if (isSelfClosing) {
            sb.append("/>");
            return;
        }
        sb.append(">");
        for (JrxNode child : children) child.renderRaw(sb);
        sb.append("</").append(tagName).append(">");
    }

    @Override
    public void render(StringBuilder sb, TemplateContext ctx) {
        if ("slot".equalsIgnoreCase(tagName)) {
            String slotHtml = ctx.getComponent()._getSlotHtml();
            if (slotHtml != null) sb.append(slotHtml);
            return;
        }

        sb.append("<").append(tagName);
        
        for (Map.Entry<String, String> attr : attributes.entrySet()) {
            String key = attr.getKey();
            String val = attr.getValue();
            
            if (val != null && val.contains("{{")) {
                val = resolveString(val, ctx);
            }

            if (key.startsWith(":")) {
                String realKey = key.substring(1);
                if (isBooleanAttr(realKey)) {
                    if (ctx.evaluate(val)) {
                        sb.append(" ").append(realKey).append("=\"").append(realKey).append("\"");
                    }
                } else {
                    sb.append(" ").append(realKey).append("=\"").append(val).append("\"");
                }
            } else {
                sb.append(" ").append(key);
                if (val != null && !val.isEmpty()) {
                    sb.append("=\"").append(val).append("\"");
                }
            }
        }

        if (isSelfClosing) {
            sb.append("/>");
            return;
        }

        sb.append(">");
        for (JrxNode child : children) child.render(sb, ctx);
        sb.append("</").append(tagName).append(">");
    }

    private String resolveString(String input, TemplateContext ctx) {
        Matcher m = VAR_PATTERN.matcher(input);
        StringBuilder res = new StringBuilder();
        String ns = ctx.getComponent().getId() + ".";
        
        while (m.find()) {
            String localKey = m.group(1);
            
            // 🧹 Limpieza del Namespace para el backend
            if (!ns.isEmpty() && localKey.startsWith(ns)) {
                localKey = localKey.substring(ns.length());
            }
            if (localKey.startsWith("this.")) {
                localKey = localKey.substring(5);
            }
            
            Object resolved = ctx.resolve(localKey);
            m.appendReplacement(res, resolved == null ? "" : Matcher.quoteReplacement(String.valueOf(resolved)));
        }
        m.appendTail(res);
        return res.toString();
    }

    private boolean isBooleanAttr(String k) {
        return switch (k) {
            case "disabled", "checked", "required", "selected", "readonly", "multiple", "hidden" -> true;
            default -> false;
        };
    }
}