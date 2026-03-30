package com.ciro.jreactive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("JrxStateToken - Criptografía y Compresión de Estado")
class JrxStateTokenTest {

    @Test
    @DisplayName("Debe codificar y decodificar correctamente un mapa de estado")
    void testEncodeDecode() throws Exception {
        Map<String, Object> state = Map.of("user", "Ciro", "role", "ADMIN", "age", 30);
        
        // Comprime y firma
        String token = JrxStateToken.encode(state);
        assertThat(token).isNotBlank().contains("."); // Debe tener payload.firma

        // Descomprime y verifica
        Map<String, Object> decoded = JrxStateToken.decode(token);
        assertThat(decoded).containsEntry("user", "Ciro")
                           .containsEntry("role", "ADMIN")
                           .containsEntry("age", 30);
    }

    @Test
    @DisplayName("Debe rechazar un token alterado (Firma Inválida)")
    void testTamperedToken() throws Exception {
        Map<String, Object> state = Map.of("money", 100);
        String token = JrxStateToken.encode(state);
        
        // Alteramos el payload (la primera parte antes del punto) como haría un hacker
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "A." + parts[1]; 
        
        // El sistema debe explotar con un SecurityException
        assertThrows(SecurityException.class, () -> JrxStateToken.decode(tampered));
    }

    @Test
    @DisplayName("Debe manejar tokens nulos o mal formados sin crashear el servidor")
    void testInvalidTokens() throws Exception {
        assertThat(JrxStateToken.decode(null)).isEmpty();
        assertThat(JrxStateToken.decode("sin_punto_de_firma")).isEmpty();
    }
}