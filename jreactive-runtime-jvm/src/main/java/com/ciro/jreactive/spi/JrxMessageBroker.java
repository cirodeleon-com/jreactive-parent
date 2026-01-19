package com.ciro.jreactive.spi;

import java.util.function.BiConsumer;

public interface JrxMessageBroker {
    // Publicar un mensaje para una sesión específica
    // El 'message' suele ser un JSON con el batch de cambios
    void publish(String sessionId, String message);

    // Registrar un callback global para procesar mensajes entrantes de cualquier sesión
    // handler.accept(sessionId, messagePayload)
    void onMessage(BiConsumer<String, String> handler);
}