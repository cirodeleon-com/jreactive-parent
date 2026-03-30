package com.ciro.jreactive.store.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RedisStoreConfig - Pruebas de Configuración de Spring")
class RedisStoreConfigTest {

    @Test
    @DisplayName("Debe crear todos los beans de configuración correctamente")
    void testConfiguracionBeans() throws Exception {
        RedisStoreConfig config = new RedisStoreConfig();

        // Simulamos la inyección de @Value de Spring Boot usando Reflexión
        setField(config, "host", "localhost");
        setField(config, "port", 6379);
        setField(config, "consistencyMode", "eventual");

        // Probamos que las fábricas de Beans funcionen
        StateSerializer fst = config.fstSerializer();
        assertThat(fst).isNotNull();
        assertThat(fst.name()).isEqualTo("fst");

        StateSerializer json = config.jsonSerializer(new ObjectMapper());
        assertThat(json).isNotNull();
        assertThat(json.name()).isEqualTo("json");

        RedisStateStore store = config.redisStateStore(fst);
        assertThat(store).isNotNull();

        assertThat(config.onlyRedis(store)).isNotNull();
        assertThat(config.hybridStore(store)).isNotNull();
        assertThat(config.redisMessageBroker()).isNotNull();
    }

    // Helper para inyectar variables privadas
    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }
}