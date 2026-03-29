package com.ciro.jreactive.ast;

import com.ciro.jreactive.HtmlComponent;
import com.ciro.jreactive.template.TemplateContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BlueprintRenderer Unit Tests (El Pintor Real del AST)")
class BlueprintRendererTest {

    // Necesitamos un componente real para el TemplateContext
    static class MyTestComponent extends HtmlComponent {
        @Override protected String template() { return ""; }
    }

    private TemplateContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new TemplateContext(new MyTestComponent());
    }

    @Test
    @DisplayName("Debe renderizar un ElementNode con atributos normales")
    void testRenderElementNode() {
        // ElementNode(tagName, isSelfClosing)
        ElementNode div = new ElementNode("div", false);
        div.attributes.put("class", "btn-primary");
        div.attributes.put("id", "submit-btn");

        StringBuilder sb = new StringBuilder();
        // Llamada estática según tu código
        BlueprintRenderer.renderNode(div, sb, ctx);

        assertThat(sb.toString()).isEqualTo("<div class=\"btn-primary\" id=\"submit-btn\"></div>");
    }

    @Test
    @DisplayName("Debe limpiar el prefijo ':' de los atributos dinámicos")
    void testRenderDynamicAttributeCleanup() {
        ElementNode input = new ElementNode("input", true);
        // Según tu línea 53: if (key.startsWith(":")) key = key.substring(1);
        input.attributes.put(":value", "nombreUsuario");
        input.attributes.put("type", "text");

        StringBuilder sb = new StringBuilder();
        BlueprintRenderer.renderNode(input, sb, ctx);

        // Verificamos que quitó el ':' pero dejó el nombre del atributo
        assertThat(sb.toString()).contains(" value=\"nombreUsuario\"");
        assertThat(sb.toString()).contains(" type=\"text\"");
    }

    @Test
    @DisplayName("Debe renderizar nodos anidados recursivamente")
    void testRenderNestedNodes() {
        ElementNode main = new ElementNode("main", false);
        ElementNode section = new ElementNode("section", false);
        section.children.add(new TextNode("Contenido Interno"));
        main.children.add(section);

        StringBuilder sb = new StringBuilder();
        BlueprintRenderer.renderNode(main, sb, ctx);

        assertThat(sb.toString()).isEqualTo("<main><section>Contenido Interno</section></main>");
    }

    @Test
    @DisplayName("Debe manejar etiquetas auto-concluidas (Self-closing)")
    void testSelfClosingTags() {
        ElementNode br = new ElementNode("br", true);
        
        StringBuilder sb = new StringBuilder();
        BlueprintRenderer.renderNode(br, sb, ctx);

        assertThat(sb.toString()).isEqualTo("<br/>");
    }

    @Test
    @DisplayName("Debe renderizar listas de nodos completas")
    void testRenderNodesList() {
        List<JrxNode> nodes = List.of(
            new ElementNode("hr", true),
            new TextNode("Separador")
        );

        StringBuilder sb = new StringBuilder();
        BlueprintRenderer.renderNodes(nodes, sb, ctx);

        assertThat(sb.toString()).isEqualTo("<hr/>Separador");
    }
}