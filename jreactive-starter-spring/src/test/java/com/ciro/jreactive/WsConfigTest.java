package com.ciro.jreactive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WsConfig - Configuración de WebSockets")
class WsConfigTest {

    @Test
    @DisplayName("Debe permitir modificar y leer las propiedades de configuración")
    void shouldGetAndSetProperties() {
        WsConfig config = new WsConfig();
        
        config.setEnabledBackpressure(false);
        config.setMaxQueue(123);
        config.setFlushIntervalMs(10);
        config.setPersistentState(false);

        assertThat(config.isEnabledBackpressure()).isFalse();
        assertThat(config.getMaxQueue()).isEqualTo(123);
        assertThat(config.getFlushIntervalMs()).isEqualTo(10);
        assertThat(config.isPersistentState()).isFalse();
    }
}