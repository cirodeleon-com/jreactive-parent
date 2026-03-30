package com.ciro.jreactive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ViewComposite - Pruebas de Composición de Vistas")
class ViewCompositeTest {

    // Necesitamos un Leaf de mentiras para probar la composición
    static class SimpleLeaf extends ViewLeaf {
        private final String content;
        SimpleLeaf(String content) { this.content = content; }
        @Override public java.util.Map<String, ReactiveVar<?>> bindings() { return java.util.Map.of(); }
        @Override public String render() { return content; }
    }

    @Test
    @DisplayName("Debe concatenar el renderizado de todos sus hijos con saltos de línea")
    void testRenderComposite() {
        ViewComposite composite = new ViewComposite();
        composite.add(new SimpleLeaf("<div>A</div>"))
                 .add(new SimpleLeaf("<div>B</div>"));

        String result = composite.render();

        // El render de ViewComposite hace un join con "\n"
        assertThat(result).isEqualTo("<div>A</div>\n<div>B</div>");
        assertThat(composite.children()).hasSize(2);
    }
}