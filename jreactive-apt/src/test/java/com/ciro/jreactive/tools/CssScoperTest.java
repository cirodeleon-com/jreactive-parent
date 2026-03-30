package com.ciro.jreactive.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@DisplayName("CssScoper - Aislamiento de Estilos en tiempo de compilación")
class CssScoperTest {

    @Test
    @DisplayName("Debe ignorar CSS vacío o nulo")
    void testEmptyOrNullCss() {
        assertThat(CssScoper.scope(null, "scope-1")).isEmpty();
        assertThat(CssScoper.scope("   ", "scope-1")).isEmpty();
    }

    @Test
    @DisplayName("Debe transformar el seudoselector :host al scope raíz")
    void testHostSelector() {
        String css = ":host { display: block; } :host:hover { color: red; }";
        String scoped = CssScoper.scope(css, "jrx-mi-comp");
        
        // Verifica que :host fue reemplazado por la clase autogenerada
        assertThat(scoped).contains(".jrx-mi-comp{display:block}");
        assertThat(scoped).doesNotContain(":host");
    }

    @Test
    @DisplayName("Debe inyectar el scope como ancestro de selectores normales")
    void testNormalSelectors() {
        String css = "button { color: red; } .primary { font-weight: bold; }";
        String scoped = CssScoper.scope(css, "jrx-btn");
        
        assertThat(scoped).contains(".jrx-btn button");
        assertThat(scoped).contains(".jrx-btn .primary");
    }

    @Test
    @DisplayName("Debe procesar selectores múltiples separados por coma de forma individual")
    void testMultipleSelectors() {
        String css = "h1, h2, h3 { margin: 0; }";
        String scoped = CssScoper.scope(css, "jrx-title");
        
        assertThat(scoped).contains(".jrx-title h1");
        assertThat(scoped).contains(".jrx-title h2");
        assertThat(scoped).contains(".jrx-title h3");
    }

    @Test
    @DisplayName("Debe respetar y procesar reglas anidadas dentro de @media queries")
    void testMediaQueries() {
        String css = "@media (max-width: 600px) { div { display: none; } }";
        String scoped = CssScoper.scope(css, "jrx-mobile");
        
        assertThat(scoped).contains("@media");
        assertThat(scoped).contains(".jrx-mobile div");
    }

    @Test
    @DisplayName("Debe manejar CSS inválido sin lanzar excepciones y devolver un string vacío")
    void testInvalidCss() {
        String css = "div { color: red; /* oops, falta cerrar la llave */";
        
        assertDoesNotThrow(() -> {
            String scoped = CssScoper.scope(css, "jrx-err");
            assertThat(scoped).isEmpty();
        });
    }
}