package com.ciro.jreactive.store.redis;

import com.ciro.jreactive.HtmlComponent;
import com.ciro.jreactive.store.StateStore;

import org.nustaq.serialization.FSTConfiguration;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RedisStateStore implements StateStore {

    private final JedisPool redisPool;
    // FST es thread-safe si se usa el singleton default
    private final FSTConfiguration conf = FSTConfiguration.createDefaultConfiguration();
    private static final int TTL_SECONDS = 1800; // 30 minutos

    public RedisStateStore(String host, int port) {
        this.redisPool = new JedisPool(host, port);
    }

    private byte[] key(String sid, String path) {
        return ("jrx:" + sid + ":" + path).getBytes();
    }

    @Override
    public HtmlComponent get(String sessionId, String path) {
        try (Jedis jedis = redisPool.getResource()) {
            byte[] data = jedis.get(key(sessionId, path));
            if (data == null) return null;
            // Deserialización ultra-rápida
            return (HtmlComponent) conf.asObject(data);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void put(String sessionId, String path, HtmlComponent component) {
        try (Jedis jedis = redisPool.getResource()) {
            // Serialización ultra-rápida
            byte[] data = conf.asByteArray(component);
            // SETEX: Set con Expiración (atómico)
            jedis.setex(key(sessionId, path), TTL_SECONDS, data);
        }
    }

    @Override
    public void remove(String sessionId, String path) {
        try (Jedis jedis = redisPool.getResource()) {
            jedis.del(key(sessionId, path));
        }
    }

    @Override
    public void removeSession(String sessionId) {
        // Borrar por patrón es costoso en Redis (SCAN). 
        // Normalmente se deja que expire solo con el TTL.
    }
}