package com.ciro.jreactive.ast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JrxParserTest {

    @Test
    @DisplayName("Debe construir un árbol AST a partir de HTML anidado")
    void shouldParseNestedHtmlToAst() {
        // Arrange
        String html = "<div class=\"container\"><h1>Titulo</h1></div>";

        // Act
        List<JrxNode> rootNodes = JrxParser.parse(html);

        // Assert
        assertThat(rootNodes).hasSize(1);
        assertThat(rootNodes.get(0)).isInstanceOf(ElementNode.class);
        
        ElementNode div = (ElementNode) rootNodes.get(0);
        assertThat(div.tagName).isEqualTo("div");
        assertThat(div.attributes).containsEntry("class", "container");
        assertThat(div.children).hasSize(1);

        ElementNode h1 = (ElementNode) div.children.get(0);
        assertThat(h1.tagName).isEqualTo("h1");
        assertThat(h1.children).hasSize(1);
        
        TextNode text = (TextNode) h1.children.get(0);
        assertThat(text.text).isEqualTo("Titulo");
    }

    @Test
    @DisplayName("Debe organizar correctamente las ramas de un bloque If/Else")
    void shouldParseIfElseBlocks() {
        // Arrange
        String html = "<div>{{#if isAdmin}}<p>Admin</p>{{else}}<span>User</span>{{/if}}</div>";

        // Act
        List<JrxNode> rootNodes = JrxParser.parse(html);

        // Assert
        ElementNode div = (ElementNode) rootNodes.get(0);
        assertThat(div.children).hasSize(1);
        assertThat(div.children.get(0)).isInstanceOf(IfNode.class);

        IfNode ifNode = (IfNode) div.children.get(0);
        assertThat(ifNode.condition).isEqualTo("isAdmin");
        
        // Verificar rama verdadera (trueBranch)
        assertThat(ifNode.trueBranch).hasSize(1);
        ElementNode p = (ElementNode) ifNode.trueBranch.get(0);
        assertThat(p.tagName).isEqualTo("p");

        // Verificar rama falsa (falseBranch)
        assertThat(ifNode.falseBranch).hasSize(1);
        ElementNode span = (ElementNode) ifNode.falseBranch.get(0);
        assertThat(span.tagName).isEqualTo("span");
    }

    @Test
    @DisplayName("Debe fallar (Verdad Funcional) si una etiqueta HTML queda sin cerrar")
    void shouldFailOnUnclosedHtmlTag() {
        // Arrange
        String html = "<div><p>Falta cerrar el div";

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            JrxParser.parse(html);
        });
        
        assertThat(exception.getMessage()).contains("Falta cerrar");
    }
    
    @Test
    @DisplayName("Debe fallar si se intenta cerrar un bloque {{/if}} sin haberlo abierto")
    void testUnexpectedBlockClose() {
        String html = "<div> {{/if}} </div>";
        
        assertThrows(IllegalStateException.class, () -> {
            JrxParser.parse(html);
        }, "Cierre {{/ ... }} inesperado");
    }

    @Test
    @DisplayName("Debe fallar si una etiqueta HTML se cierra antes que un bloque de plantilla")
    void testMismatchedHtmlAndTemplateClosing() {
        // Estructura cruzada: <div> {{#if}} </div> {{/if}} -> INCORRECTO
        String html = "<div> {{#if activo}} </div> {{/if}}";
        
        assertThrows(IllegalStateException.class, () -> {
            JrxParser.parse(html);
        }, "El bloque {{#if}} no ha sido cerrado");
    }

    @Test
    @DisplayName("Debe fallar si el HTML termina y faltan etiquetas por cerrar")
    void testUnclosedTagsAtEnd() {
        String html = "<div><span>Contenido"; // Falta </span> y </div>
        
        assertThrows(IllegalStateException.class, () -> {
            JrxParser.parse(html);
        });
    }

    @Test
    @DisplayName("Debe parsear correctamente etiquetas de Web Components (Custom Elements)")
    void testParseCustomElements() {
        String html = "<my-custom-element attr='1'> <slot/> </my-custom-element>";
        List<JrxNode> nodes = JrxParser.parse(html);
        
        assertThat(nodes).hasSize(1);
        ElementNode el = (ElementNode) nodes.get(0);
        assertThat(el.tagName).isEqualTo("my-custom-element");
    }
    
    

    

    
}