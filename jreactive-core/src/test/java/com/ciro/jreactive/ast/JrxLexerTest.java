package com.ciro.jreactive.ast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static com.ciro.jreactive.ast.JrxLexer.TokenType.*;

class JrxLexerTest {

    @Test
    @DisplayName("Debe tokenizar correctamente HTML simple con variables")
    void shouldLexSimpleHtmlAndVariables() {
        // Arrange
        String html = "<div id=\"main\">Hola {{user.name}}</div>";

        // Act
        List<JrxLexer.Token> tokens = JrxLexer.lex(html);

        // Assert
        assertThat(tokens).hasSize(3);

        // 1. Etiqueta de apertura
        assertThat(tokens.get(0).type()).isEqualTo(TAG_OPEN);
        assertThat(tokens.get(0).name()).isEqualTo("div");
        assertThat(tokens.get(0).content()).isEqualTo("id=\"main\"");
        assertThat(tokens.get(0).selfClosing()).isFalse();

        // 2. Texto + Variable (El lexer de JReactive delega las llaves al TextNode)
        assertThat(tokens.get(1).type()).isEqualTo(TEXT);
        assertThat(tokens.get(1).content()).isEqualTo("Hola {{user.name}}");

        // 3. Etiqueta de cierre
        assertThat(tokens.get(2).type()).isEqualTo(TAG_CLOSE);
        assertThat(tokens.get(2).name()).isEqualTo("div");
    }

    @Test
    @DisplayName("Debe detectar bloques reactivos de plantilla (#if, #each)")
    void shouldLexTemplateBlocks() {
        // Arrange
        String html = "{{#if isActive}}<p>Visible</p>{{/if}}";

        // Act
        List<JrxLexer.Token> tokens = JrxLexer.lex(html);

        // Assert
        assertThat(tokens).hasSize(5);

        assertThat(tokens.get(0).type()).isEqualTo(BLOCK_OPEN);
        assertThat(tokens.get(0).name()).isEqualTo("if isActive");

        assertThat(tokens.get(1).type()).isEqualTo(TAG_OPEN);
        assertThat(tokens.get(1).name()).isEqualTo("p");

        assertThat(tokens.get(2).type()).isEqualTo(TEXT);
        assertThat(tokens.get(2).content()).isEqualTo("Visible");

        assertThat(tokens.get(3).type()).isEqualTo(TAG_CLOSE);
        assertThat(tokens.get(3).name()).isEqualTo("p");

        assertThat(tokens.get(4).type()).isEqualTo(BLOCK_CLOSE);
        assertThat(tokens.get(4).name()).isEqualTo("if"); // Tu lexer limpia el contenido
    }
    
    @Test
    @DisplayName("Debe manejar etiquetas auto-cerradas correctamente")
    void shouldHandleSelfClosingTags() {
        // Arrange
        String html = "<input type=\"text\" required />";

        // Act
        List<JrxLexer.Token> tokens = JrxLexer.lex(html);

        // Assert
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).type()).isEqualTo(TAG_OPEN);
        assertThat(tokens.get(0).name()).isEqualTo("input");
        assertThat(tokens.get(0).selfClosing()).isTrue();
    }
}