package com.ciro.jreactive.ast;

import com.ciro.jreactive.template.TemplateContext;
import java.util.ArrayList;
import java.util.List;

public class EachNode implements JrxNode {
    public String listExpression;
    public final String alias;
    public final List<JrxNode> children = new ArrayList<>();

    public EachNode(String listExpression, String alias) {
        this.listExpression = listExpression;
        this.alias = alias;
    }

    @Override
    public void renderRaw(StringBuilder sb) {
        // En crudo, mantenemos la estructura de la etiqueta template
        sb.append("<template data-each=\"").append(listExpression).append(":").append(alias).append("\">");
        for (JrxNode child : children) {
            child.renderRaw(sb); 
        }
        sb.append("</template>");
    }

    @Override
    public void render(StringBuilder sb, TemplateContext ctx) {
        sb.append("<template data-each=\"")
          .append(listExpression)
          .append(":")
          .append(alias)
          .append("\">");

        BlueprintRenderer.renderNodes(children, sb, ctx);

        sb.append("</template>");
    }
}