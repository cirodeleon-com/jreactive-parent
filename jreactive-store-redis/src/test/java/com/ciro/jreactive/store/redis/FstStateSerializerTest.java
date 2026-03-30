package com.ciro.jreactive.store.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FstStateSerializer - Pruebas de Serialización Binaria Rápida")
class FstStateSerializerTest {

    @Test
    @DisplayName("Debe serializar y deserializar datos binarios con FST")
    void testFstSerialization() {
        // 1. Arrange
        FstStateSerializer serializer = new FstStateSerializer();
        String datoSecreto = "Datos ultra rápidos";

        // 2. Act
        byte[] bytes = serializer.serialize(datoSecreto);
        assertThat(bytes).isNotNull().isNotEmpty();

        // 3. Assert
        String resultado = serializer.deserialize(bytes, String.class);
        assertThat(resultado).isEqualTo(datoSecreto);
    }
}