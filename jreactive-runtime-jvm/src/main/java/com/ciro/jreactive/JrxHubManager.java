/* === File: jreactive-runtime-jvm/src/main/java/com/ciro/jreactive/JrxHubManager.java === */
package com.ciro.jreactive;

import com.ciro.jreactive.spi.JrxMessageBroker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.*;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class JrxHubManager {

    private static final class Key {
        private final String sessionId;
        private final String path;

        Key(String sessionId, String path) {
            this.sessionId = sessionId;
            this.path = path;
        }

        String sessionId() { return sessionId; }
        String path() { return path; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key)) return false;
            Key k = (Key) o;
            return Objects.equals(sessionId, k.sessionId) && Objects.equals(path, k.path);
        }

        @Override public int hashCode() {
            return Objects.hash(sessionId, path);
        }
    }

    private final PageResolver pageResolver;
    private final ObjectMapper mapper;
    private final Cache<Key, JrxPushHub> hubs;
    private final JrxMessageBroker broker; // 🔥 Dependencia nueva (puede ser null)

    // Constructor actualizado para recibir el Broker
    public JrxHubManager(PageResolver pageResolver, ObjectMapper mapper, JrxMessageBroker broker) {
        this.pageResolver = pageResolver;
        this.mapper = mapper;
        this.broker = broker;

        this.hubs = Caffeine.newBuilder()
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .maximumSize(5_000)
                .removalListener((Key k, JrxPushHub hub, RemovalCause cause) -> {
                    if (hub != null) hub.close();
                })
                .build();
        
        // 🔥 Si hay broker, nos suscribimos al tráfico global
        if (this.broker != null) {
            this.broker.onMessage(this::distributeRemoteMessage);
        }
    }
    
    // Este método se dispara cuando Redis dice: "Hubo cambio en la sesión X"
    private void distributeRemoteMessage(String sessionId, String message) {
        // Buscamos si tenemos algún Hub local conectado para esa sesión.
        // Caffeine no indexa por sessionId, así que iteramos (rápido en RAM).
        hubs.asMap().forEach((key, hub) -> {
            if (key.sessionId().equals(sessionId)) {
                // ¡Bingo! El usuario está conectado a este servidor.
                // Inyectamos el mensaje directo al socket (Fast Path).
                hub.emitRaw(message);
            }
        });
    }

    public JrxPushHub hub(String sessionId, String path) {
        Key key = new Key(sessionId, path);
        return hubs.get(key, _k -> {
            HtmlComponent page = pageResolver.getPage(sessionId, path);
            
            if (page._state() == ComponentState.UNMOUNTED) {
            	JrxPushHub old = hubs.getIfPresent(key);
                if (old != null) old.close();
                //hubs.invalidate(key);

                page._initIfNeeded();
                page._mountRecursive();
            }
            // Pasamos el broker y la sessionId al Hub para que pueda PUBLICAR
            return new JrxPushHub(page, mapper, 2_000, broker, sessionId);
        });
    }

    public void evictAll(String sessionId) {
        hubs.asMap().keySet().removeIf(k -> k.sessionId().equals(sessionId));
    }
    
    public void evict(String sessionId, String path) {
        Key key = new Key(sessionId, path);
        JrxPushHub hub = hubs.getIfPresent(key);
        if (hub != null) hub.close();
        hubs.invalidate(key);
    }

}