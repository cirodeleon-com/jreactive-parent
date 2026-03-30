package com.ciro.jreactive.ast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IfNode Unit Tests")
class IfNodeTest {

    @Test
    @DisplayName("Debe renderizar ramas if y else en modo raw correctamente")
    void testRenderRawWithElse() {
        IfNode node = new IfNode("admin");
        node.trueBranch.add(new TextNode("Es Admin"));
        node.falseBranch.add(new TextNode("Es User"));
        
        StringBuilder sb = new StringBuilder();
        node.renderRaw(sb);
        
        String result = sb.toString();
        assertThat(result).contains("<template data-if=\"admin\">Es Admin</template>");
        assertThat(result).contains("<template data-else=\"admin\">Es User</template>");
    }
    
    @Test
    @DisplayName("Debe renderizar ambas ramas en el template final para el cliente")
    void testRenderLifecycleWithBranches() {
        IfNode node = new IfNode("estaActivo");
        node.trueBranch.add(new TextNode("VERDAD"));
        node.falseBranch.add(new TextNode("FALSO"));
        
        StringBuilder sb = new StringBuilder();
        com.ciro.jreactive.HtmlComponent dummy = new com.ciro.jreactive.HtmlComponent() {
            @Override protected String template() { return ""; }
        };
        com.ciro.jreactive.template.TemplateContext ctx = new com.ciro.jreactive.template.TemplateContext(dummy);

        node.render(sb, ctx);

        String result = sb.toString();
        assertThat(result).contains("data-if=\"estaActivo\">VERDAD");
        assertThat(result).contains("data-else=\"estaActivo\">FALSO");
    }
}