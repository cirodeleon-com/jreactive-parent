package com.ciro.jreactive; // 👈 Ahora vive en el Runtime JVM

import com.ciro.jreactive.spi.JrxMessageBroker;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Broker en memoria RAM. Permite que el @Shared Multijugador funcione 
 * sin necesidad de tener Redis instalado durante el desarrollo.
 */
public class LocalMessageBroker implements JrxMessageBroker {

    private final Map<String, String> sharedDb = new ConcurrentHashMap<>();
    private final List<BiConsumer<String, String>> listeners = new CopyOnWriteArrayList<>();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void publish(String sessionId, String message) {
        listeners.forEach(l -> l.accept(sessionId, message));
    }

    @Override
    public void onMessage(BiConsumer<String, String> handler) {
        listeners.add(handler);
    }

    @Override
    public void publishShared(String topic, String message) {
        // Simulamos el Pub/Sub enviando el mensaje a todos los listeners locales
        listeners.forEach(l -> l.accept("shared:" + topic, message));
    }

    @Override
    public void saveSharedState(String topic, String varName, Object value) {
        try {
            sharedDb.put(topic + "::" + varName, mapper.writeValueAsString(value));
        } catch (Exception e) {
        	System.err.println("❌ [JReactive LocalBroker] Error guardando estado compartido para '" + varName + "': " + e.getMessage());
        }
    }

    @Override
    public Map<String, String> getSharedState(String topic) {
        Map<String, String> result = new HashMap<>();
        String prefix = topic + "::";
        sharedDb.forEach((k, v) -> {
            if (k.startsWith(prefix)) {
                result.put(k.substring(prefix.length()), v);
            }
        });
        return result;
    }
}