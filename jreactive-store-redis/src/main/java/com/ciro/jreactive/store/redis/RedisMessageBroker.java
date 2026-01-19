package com.ciro.jreactive.store.redis;

import com.ciro.jreactive.spi.JrxMessageBroker;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

public class RedisMessageBroker implements JrxMessageBroker {

    private final JedisPool pool;
    private final String serverId; // ID único de este nodo (pod) para evitar eco
    private final ExecutorService listenerThread = Executors.newSingleThreadExecutor();
    private volatile BiConsumer<String, String> handler;

    private static final String CHANNEL_PREFIX = "jrx:upd:";
    private static final String CHANNEL_PATTERN = "jrx:upd:*";

    public RedisMessageBroker(String host, int port) {
        this.pool = new JedisPool(host, port);
        // Generamos un ID aleatorio al arrancar para identificarnos en el cluster
        this.serverId = UUID.randomUUID().toString();
        startListener();
    }

    private void startListener() {
        listenerThread.submit(() -> {
            // Este hilo se queda bloqueado escuchando a Redis por siempre
            try (Jedis jedis = pool.getResource()) {
                jedis.psubscribe(new JedisPubSub() {
                    @Override
                    public void onPMessage(String pattern, String channel, String message) {
                        if (handler == null) return;

                        // message formato: "SERVER_UUID|{json_payload}"
                        int sep = message.indexOf('|');
                        if (sep > 0) {
                            String senderId = message.substring(0, sep);
                            String payload = message.substring(sep + 1);

                            // 🔥 ECO-CHECK: Si el mensaje lo envié yo mismo, lo ignoro.
                            // Ya se procesó localmente en el WebSocket.
                            if (serverId.equals(senderId)) {
                                return;
                            }

                            // Extraer Session ID del canal "jrx:upd:SESSION_123"
                            String sessionId = channel.substring(CHANNEL_PREFIX.length());
                            
                            // Entregar al Runtime para que decida si tiene esa sesión conectada
                            handler.accept(sessionId, payload);
                        }
                    }
                }, CHANNEL_PATTERN);
            } catch (Exception e) {
                System.err.println("❌ Redis Pub/Sub desconectado: " + e.getMessage());
                // TODO: Aquí iría lógica de reconexión con backoff
            }
        });
    }

    @Override
    public void publish(String sessionId, String message) {
        try (Jedis jedis = pool.getResource()) {
            // Firmamos el mensaje con nuestro ID
            String envelope = serverId + "|" + message;
            jedis.publish(CHANNEL_PREFIX + sessionId, envelope);
        } catch (Exception e) {
            System.err.println("❌ Error publicando a Redis: " + e.getMessage());
        }
    }

    @Override
    public void onMessage(BiConsumer<String, String> handler) {
        this.handler = handler;
    }
}