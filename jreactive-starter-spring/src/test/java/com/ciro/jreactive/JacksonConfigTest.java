package com.ciro.jreactive;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JacksonConfig - Configuración del Mapper JSON")
class JacksonConfigTest {

    @Test
    @DisplayName("Debe crear e inicializar el ObjectMapper con los módulos necesarios")
    void shouldCreateObjectMapper() {
        JacksonConfig config = new JacksonConfig();
        ObjectMapper mapper = config.objectMapper();
        
        assertThat(mapper).isNotNull();
        // Verificamos que los módulos se hayan registrado con los IDs reales que escupe Jackson
        assertThat(mapper.getRegisteredModuleIds()).contains(
            "jackson-datatype-jsr310", 
            "com.fasterxml.jackson.datatype.jdk8.Jdk8Module", // <-- El nombre real del módulo
            "jackson-module-parameter-names"
        );
    }
}