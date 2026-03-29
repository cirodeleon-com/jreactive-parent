package com.ciro.jreactive.router;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class PathPatternTest {

    @Test
    @DisplayName("Debe compilar una ruta con variables y extraer los parámetros correctamente")
    void testValidPathMatching() {
        // Arrange
        PathPattern pattern = PathPattern.compile("/usuarios/{id}/compras/{orden}");

        // Act
        Map<String, String> resultado = pattern.match("/usuarios/45/compras/999");

        // Assert
        assertThat(resultado).isNotNull();
        assertThat(resultado).containsEntry("id", "45");
        assertThat(resultado).containsEntry("orden", "999");
    }

    @Test
    @DisplayName("Debe devolver null si la ruta no coincide con el patrón")
    void testInvalidPathMatching() {
        PathPattern pattern = PathPattern.compile("/usuarios/{id}");
        
        // Faltan parámetros o la estructura es distinta
        assertThat(pattern.match("/productos/45")).isNull(); 
        assertThat(pattern.match("/usuarios/45/extra")).isNull();
    }
}