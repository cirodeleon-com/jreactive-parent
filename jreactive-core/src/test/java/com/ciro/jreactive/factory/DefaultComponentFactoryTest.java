package com.ciro.jreactive.factory;

import com.ciro.jreactive.HtmlComponent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultComponentFactoryTest {

    // Componente válido de prueba
    static class DummyComponent extends HtmlComponent {
        @Override protected String template() { return ""; }
    }

    // Clase inválida (No tiene constructor vacío)
    static class BadComponent {
        private BadComponent(int a) {} 
    }

    @Test
    @DisplayName("Debe instanciar correctamente un componente válido")
    void testCreateValidComponent() {
        DefaultComponentFactory factory = new DefaultComponentFactory();
        DummyComponent comp = factory.create(DummyComponent.class);
        
        assertThat(comp).isNotNull();
    }

    @Test
    @DisplayName("Debe fallar con IllegalStateException si la clase no tiene constructor vacío")
    void testFailOnInvalidComponent() {
        DefaultComponentFactory factory = new DefaultComponentFactory();
        
        assertThrows(IllegalStateException.class, () -> {
            factory.create(BadComponent.class);
        });
    }
}