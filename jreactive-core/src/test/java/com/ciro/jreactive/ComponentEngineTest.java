package com.ciro.jreactive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("ComponentEngine - Fachada de Estrategia")
class ComponentEngineTest {

    // Componente dummy para actuar como padre
    static class DummyParent extends HtmlComponent {
        @Override protected String template() { return ""; }
    }

    @Test
    @DisplayName("Debe devolver vacío si se intenta renderizar un hijo sin una estrategia seteada")
    void testRenderChildWithoutStrategy() throws Exception {
        // 1. Usamos reflexión para poner la estrategia en null (simulando estado inicial)
        Field strategyField = ComponentEngine.class.getDeclaredField("strategy");
        strategyField.setAccessible(true);
        Object originalStrategy = strategyField.get(null);
        strategyField.set(null, null);

        try {
            DummyParent padre = new DummyParent();
            // 2. Act: Intentamos renderizar un hijo
            String result = ComponentEngine.renderChild(padre, "CualquierCosa", java.util.Map.of(), java.util.Map.of());
            
            // 3. Assert: Según tu código (línea 31 de ComponentEngine.java), debe retornar ""
            assertThat(result).isEmpty();
        } finally {
            // Restauramos la estrategia original para no romper otros tests
            strategyField.set(null, originalStrategy);
        }
    }

    @Test
    @DisplayName("Debe lanzar IllegalStateException si se intenta renderizar un componente raíz sin estrategia")
    void testRenderRootWithoutStrategy() throws Exception {
        Field strategyField = ComponentEngine.class.getDeclaredField("strategy");
        strategyField.setAccessible(true);
        Object originalStrategy = strategyField.get(null);
        strategyField.set(null, null);

        try {
            DummyParent root = new DummyParent();
            // Según tu línea 22 de ComponentEngine.java, esto debe explotar con el mensaje educativo
            assertThrows(IllegalStateException.class, () -> ComponentEngine.render(root));
        } finally {
            strategyField.set(null, originalStrategy);
        }
    }
}