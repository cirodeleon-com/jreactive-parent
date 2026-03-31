package com.ciro.jreactive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WsResourcesConfig - Configuración de Recursos")
class WsResourcesConfigTest {

    @Test
    @DisplayName("Debe crear un ScheduledExecutorService para el manejo de hilos")
    void shouldCreateExecutorService() {
        WsResourcesConfig config = new WsResourcesConfig();
        ScheduledExecutorService executor = config.jreactiveExecutor();
        
        assertThat(executor).isNotNull();
        assertThat(executor.isShutdown()).isFalse();
        
        // Limpiamos los hilos creados por la prueba
        executor.shutdownNow();
    }
}