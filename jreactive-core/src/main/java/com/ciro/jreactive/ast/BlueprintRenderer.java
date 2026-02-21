package com.ciro.jreactive.ast;

import com.ciro.jreactive.template.TemplateContext;
import java.util.List;
import java.util.Map;

public final class BlueprintRenderer {

    private BlueprintRenderer() {}

    public static void renderNodes(List<JrxNode> nodes, StringBuilder sb, TemplateContext ctx) {
        for (JrxNode n : nodes) renderNode(n, sb, ctx);
    }

    public static void renderNode(JrxNode n, StringBuilder sb, TemplateContext ctx) {
        // Bloques: usar su render() (los vamos a parchear para que produzcan templates bien)
        if (n instanceof IfNode || n instanceof EachNode) {
            n.render(sb, ctx);
            return;
        }

        // Textos: raw (no resolver {{...}})
        if (n instanceof TextNode t) {
            t.renderRaw(sb);
            return;
        }

        // Elementos / Componentes (ComponentNode hereda de ElementNode)
        if (n instanceof ElementNode el) {
            renderElementBlueprint(el, sb, ctx);
            return;
        }

        // fallback
        n.renderRaw(sb);
    }

    private static void renderElementBlueprint(ElementNode el, StringBuilder sb, TemplateContext ctx) {
        // 🔥 SLOT EXPAND: aquí está la magia
        if ("slot".equalsIgnoreCase(el.tagName)) {
            String slotHtml = ctx.getComponent()._getSlotHtml();
            if (slotHtml != null) sb.append(slotHtml);
            return;
        }

        sb.append("<").append(el.tagName);

        for (Map.Entry<String, String> e : el.attributes.entrySet()) {
            String key = e.getKey();
            String val = e.getValue();

            // En RAW, quitamos ":" igual que tu renderRaw actual
            if (key.startsWith(":")) key = key.substring(1);

            sb.append(" ").append(key);
            if (val != null && !val.isEmpty()) {
                sb.append("=\"").append(val).append("\"");
            }
        }

        if (el.isSelfClosing) {
            sb.append("/>");
            return;
        }

        sb.append(">");
        for (JrxNode child : el.children) renderNode(child, sb, ctx);
        sb.append("</").append(el.tagName).append(">");
    }
}