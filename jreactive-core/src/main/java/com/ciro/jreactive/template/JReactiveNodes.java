package com.ciro.jreactive.template;

import java.util.*;

// 1. Nodo Contenedor (Mantiene el orden y anidamiento)
class ContainerNode implements TemplateNode {
    protected final List<TemplateNode> children = new ArrayList<>();
    protected List<TemplateNode> elseChildren = null;
    protected boolean usingElse = false;

    public void addChild(TemplateNode node) {
        if (usingElse) {
            if (elseChildren == null) elseChildren = new ArrayList<>();
            elseChildren.add(node);
        } else {
            children.add(node);
        }
    }

    public void enableElse() { this.usingElse = true; }

    @Override
    public void render(StringBuilder sb, TemplateContext ctx) {
        for (TemplateNode child : children) child.render(sb, ctx);
    }
}

// 2. Texto Plano
record TextNode(String text) implements TemplateNode {
    @Override public void render(StringBuilder sb, TemplateContext ctx) { sb.append(text); }
}

// 3. Variable {{ var }} -> Se deja intacta para que el JS la vea
record VarNode(String path) implements TemplateNode {
    @Override public void render(StringBuilder sb, TemplateContext ctx) {
        sb.append("{{").append(path).append("}}");
    }
}

// 4. Bloque IF -> <template data-if="...">
class IfNode extends ContainerNode {
    private final String condition;
    public IfNode(String condition) { this.condition = condition; }

    @Override
    public void render(StringBuilder sb, TemplateContext ctx) {
        sb.append("<template data-if=\"").append(condition).append("\">");
        // Renderizamos los hijos del IF
        for (TemplateNode child : children) child.render(sb, ctx);
        sb.append("</template>");

        // Si hay un ELSE, generamos su respectivo tag
        if (elseChildren != null) {
            sb.append("<template data-else=\"").append(condition).append("\">");
            for (TemplateNode child : elseChildren) child.render(sb, ctx);
            sb.append("</template>");
        }
    }
}

// 5. Bloque EACH -> <template data-each="...">
class EachNode extends ContainerNode {
    private final String listPath;
    private final String alias;

    public EachNode(String listPath, String alias) {
        this.listPath = listPath;
        this.alias = alias;
    }

    @Override
    public void render(StringBuilder sb, TemplateContext ctx) {
        sb.append("<template data-each=\"").append(listPath).append(":").append(alias).append("\">");
        // Renderizamos el cuerpo del bucle una sola vez (como plantilla para el JS)
        for (TemplateNode child : children) child.render(sb, ctx);
        sb.append("</template>");
    }
}