package com.ciro.jreactive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LocalMessageBroker - Pruebas del Broker en Memoria")
class LocalMessageBrokerTest {

    private LocalMessageBroker broker;

    @BeforeEach
    void setUp() {
        broker = new LocalMessageBroker();
    }

    @Test
    @DisplayName("Debe publicar y recibir mensajes privados")
    void testPrivateMessaging() {
        AtomicReference<String> receivedSession = new AtomicReference<>();
        AtomicReference<String> receivedMessage = new AtomicReference<>();

        broker.onMessage((sid, msg) -> {
            receivedSession.set(sid);
            receivedMessage.set(msg);
        });

        broker.publish("session-123", "Hola Mundo");

        assertThat(receivedSession.get()).isEqualTo("session-123");
        assertThat(receivedMessage.get()).isEqualTo("Hola Mundo");
    }

    @Test
    @DisplayName("Debe publicar y recibir mensajes compartidos (Salas/Topics)")
    void testSharedMessaging() {
        AtomicReference<String> receivedTopic = new AtomicReference<>();
        AtomicReference<String> receivedMessage = new AtomicReference<>();

        broker.onMessage((sid, msg) -> {
            receivedTopic.set(sid);
            receivedMessage.set(msg);
        });

        broker.publishShared("sala-global", "Alerta a todos");

        // El broker local prefija el topic con "shared:"
        assertThat(receivedTopic.get()).isEqualTo("shared:sala-global");
        assertThat(receivedMessage.get()).isEqualTo("Alerta a todos");
    }

    @Test
    @DisplayName("Debe guardar y recuperar estado compartido")
    void testSharedStateStorage() {
        broker.saveSharedState("sala-1", "chat", "Mensaje 1");
        broker.saveSharedState("sala-1", "contador", 5);

        Map<String, String> state = broker.getSharedState("sala-1");

        assertThat(state).hasSize(2);
        assertThat(state.get("chat")).contains("Mensaje 1");
        assertThat(state.get("contador")).isEqualTo("5");
        
        // No debe mezclar salas
        assertThat(broker.getSharedState("sala-2")).isEmpty();
    }
}