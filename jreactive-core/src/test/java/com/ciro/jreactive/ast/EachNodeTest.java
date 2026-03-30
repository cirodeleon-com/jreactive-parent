package com.ciro.jreactive.ast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EachNode Unit Tests")
class EachNodeTest {

    @Test
    @DisplayName("Debe renderizar la estructura raw para el motor de JS")
    void testRenderRaw() {
        EachNode node = new EachNode("items", "item");
        node.children.add(new TextNode("<li>{{item}}</li>"));
        
        StringBuilder sb = new StringBuilder();
        node.renderRaw(sb);
        
        assertThat(sb.toString())
            .isEqualTo("<template data-each=\"items:item\"><li>{{item}}</li></template>");
    }
    
    @Test
    @DisplayName("Debe ejecutar el renderizado de sus nodos hijos en el ciclo normal")
    void testRenderLifecycle() {
        EachNode node = new EachNode("items", "i");
        node.children.add(new TextNode("Hijo"));
        
        StringBuilder sb = new StringBuilder();
        // Usamos un componente dummy para el contexto
        com.ciro.jreactive.HtmlComponent dummy = new com.ciro.jreactive.HtmlComponent() {
            @Override protected String template() { return ""; }
        };
        com.ciro.jreactive.template.TemplateContext ctx = new com.ciro.jreactive.template.TemplateContext(dummy);

        node.render(sb, ctx);

        // Verifica que se mantenga la etiqueta template pero procese los hijos
        assertThat(sb.toString()).contains("<template data-each=\"items:i\">Hijo</template>");
    }
}