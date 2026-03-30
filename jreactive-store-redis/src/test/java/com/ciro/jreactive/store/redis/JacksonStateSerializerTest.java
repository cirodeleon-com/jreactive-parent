package com.ciro.jreactive.store.redis;

import com.ciro.jreactive.annotations.Shared;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JacksonStateSerializer - Pruebas de Serialización JSON")
class JacksonStateSerializerTest {

    @Test
    @DisplayName("Debe serializar y deserializar un objeto correctamente")
    void testSerializeAndDeserialize() {
        // 1. Arrange
        ObjectMapper mapper = new ObjectMapper();
        JacksonStateSerializer serializer = new JacksonStateSerializer(mapper);
        String mensajeOriginal = "La Verdad Funcional vive en Redis";

        // 2. Act (Serializar a bytes)
        byte[] bytes = serializer.serialize(mensajeOriginal);
        assertThat(bytes).isNotNull().isNotEmpty();

        // 3. Act (Deserializar de vuelta a String)
        String mensajeRecuperado = serializer.deserialize(bytes, String.class);

        // 4. Assert
        assertThat(mensajeRecuperado).isEqualTo(mensajeOriginal);
    }
    
    public static class DummyShared {
        public String visible = "dato_publico";
        
        @Shared("mi-topic") 
        public String oculto = "secreto_multijugador";
    }

    @Test
    @DisplayName("Debe ignorar los campos con @Shared al serializar")
    void testIgnoraShared() {
        ObjectMapper mapper = new ObjectMapper();
        JacksonStateSerializer serializer = new JacksonStateSerializer(mapper);
        
        byte[] bytes = serializer.serialize(new DummyShared());
        String json = new String(bytes); // Convertimos los bytes a String para ver el JSON
        
        // Assert: El campo normal debe estar, pero el @Shared debe ser ignorado
        assertThat(json).contains("dato_publico");
        assertThat(json).doesNotContain("secreto_multijugador");
    }
}