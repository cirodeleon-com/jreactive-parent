package com.ciro.jreactive.store.redis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RedisMessageBroker - Test de Resiliencia")
class RedisMessageBrokerTest {

    @Test
    @DisplayName("Debe manejar gracefully la falta de conexión al Pub/Sub")
    void testBrokerFailures() {
        // Apuntamos a un puerto falso
        RedisMessageBroker broker = new RedisMessageBroker("localhost", 9999);
        
        // Configuramos un handler vacío
        broker.onMessage((sid, msg) -> {});

        // Todas estas peticiones fallarán por debajo, pero la app no debe hacer crash
        broker.publish("sid1", "mensaje");
        broker.publishShared("topic", "mensaje");
        broker.saveSharedState("topic", "var", "val");
        
        var state = broker.getSharedState("topic");
        assertThat(state).isEmpty();
    }
}