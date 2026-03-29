package com.ciro.jreactive.spi;

import com.ciro.jreactive.HtmlComponent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccessorRegistryTest {

    static class FakeComponent extends HtmlComponent {
        @Override protected String template() { return ""; }
    }

    @Test
    @DisplayName("Debe devolver null de forma segura cuando el AOT no está disponible (Fallback)")
    void testRegistryFallbackWithoutAot() {
        // Act: Pedimos el accessor de una clase que no fue compilada por el APT
        ComponentAccessor<?> accessor = AccessorRegistry.get(FakeComponent.class);
        
        // Assert: El registry debe manejar la excepción interna y devolver null
        assertThat(accessor).isNull();
        assertThat(AccessorRegistry.has(FakeComponent.class)).isFalse();
    }
    
 // Clase que simulará tener un Accessor registrado
    static class GeneratedComponent extends HtmlComponent {
        @Override protected String template() { return ""; }
    }

    @Test
    @DisplayName("Debe registrar y recuperar Accessors manualmente (Simulando código generado)")
    void testManualRegistration() {
        // 1. Creamos un Accessor de mentiras
        ComponentAccessor<GeneratedComponent> myMockAccessor = new ComponentAccessor<>() {
            @Override public void write(GeneratedComponent c, String p, Object v) {}
            @Override public Object read(GeneratedComponent c, String p) { return "DATOS_AOT"; }
            @Override public Object call(GeneratedComponent c, String m, Object... a) { return null; }
        };

        // 2. Act: Registramos manualmente (como lo haría el bloque static de una clase __Accessor)
        AccessorRegistry.register(GeneratedComponent.class, myMockAccessor);

        // 3. Assert: Recuperamos y verificamos que no sea null
        ComponentAccessor<GeneratedComponent> retrieved = AccessorRegistry.get(GeneratedComponent.class);
        
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.read(new GeneratedComponent(), "any")).isEqualTo("DATOS_AOT");
        assertThat(AccessorRegistry.has(GeneratedComponent.class)).isTrue();
    }

    @Test
    @DisplayName("Debe ser consistente al preguntar varias veces por una clase inexistente (Caché NO_OP)")
    void testNoOpCacheConsistency() {
        // Primera vez: Intenta cargar clase dinámicamente y falla
        AccessorRegistry.get(FakeComponent.class);
        
        // Segunda vez: Debe retornar null directamente desde el caché interno
        assertThat(AccessorRegistry.has(FakeComponent.class)).isFalse();
    }
}