package com.ciro.jreactive.ast;

import com.ciro.jreactive.template.TemplateContext;
import java.util.ArrayList;
import java.util.List;

public class IfNode implements JrxNode {
    public String condition;
    public final List<JrxNode> trueBranch = new ArrayList<>();
    public final List<JrxNode> falseBranch = new ArrayList<>();
    public boolean inElse = false;

    public IfNode(String condition) { this.condition = condition; }

    @Override
    public void renderRaw(StringBuilder sb) {
        // Preservar la estructura del template
        sb.append("<template data-if=\"").append(condition).append("\">");
        for (JrxNode n : trueBranch) n.renderRaw(sb);
        sb.append("</template>");
        
        if (!falseBranch.isEmpty()) {
            sb.append("<template data-else=\"").append(condition).append("\">");
            for (JrxNode n : falseBranch) n.renderRaw(sb);
            sb.append("</template>");
        }
    }

    @Override
    public void render(StringBuilder sb, TemplateContext ctx) {
        // Template para el cliente (pero con slot ya expandido)
        sb.append("<template data-if=\"").append(condition).append("\">");
        BlueprintRenderer.renderNodes(trueBranch, sb, ctx);
        sb.append("</template>");

        if (!falseBranch.isEmpty()) {
            sb.append("<template data-else=\"").append(condition).append("\">");
            BlueprintRenderer.renderNodes(falseBranch, sb, ctx);
            sb.append("</template>");
        }
    }
}