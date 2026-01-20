package com.ciro.jreactive.store.redis;

import com.ciro.jreactive.HtmlComponent;
import com.ciro.jreactive.store.StateStore;

import org.nustaq.serialization.FSTConfiguration;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;

import java.util.Set;

public class RedisStateStore implements StateStore {

    private final JedisPool redisPool;
    // FST es thread-safe si se usa el singleton default
    private final FSTConfiguration conf = FSTConfiguration.createDefaultConfiguration();
    private static final int TTL_SECONDS = 1800; // 30 minutos
    
    private static final String CAS_SCRIPT = """
            local key = KEYS[1]
            local expectedVer = tonumber(ARGV[1])
            local newData = ARGV[2]
            
            -- Si no existe la clave, solo permitimos si esperamos versión 0 (creación)
            if redis.call('exists', key) == 0 then
                if expectedVer == 0 then
                    redis.call('hset', key, 'data', newData, 'v', 1)
                    redis.call('expire', key, 1800)
                    return 1
                else
                    return 0
                end
            end

            -- Verificamos versión actual
            local currentVer = tonumber(redis.call('hget', key, 'v') or '0')
            if currentVer == expectedVer then
                redis.call('hset', key, 'data', newData, 'v', currentVer + 1)
                redis.call('expire', key, 1800)
                return 1
            else
                return 0
            end
        """;

    public RedisStateStore(String host, int port) {
        this.redisPool = new JedisPool(host, port);
    }

    private byte[] key(String sid, String path) {
        return ("jrx:page:" + sid + ":" + path).getBytes();
    }
    
    // Clave para el índice (Set) que rastrea qué páginas tiene una sesión
    private String indexKey(String sid) {
        return "jrx:idx:" + sid;
    }

    @Override
    public HtmlComponent get(String sessionId, String path) {
        try (Jedis jedis = redisPool.getResource()) {
            byte[] data = jedis.hget(key(sessionId, path), "data".getBytes());
            if (data == null) return null;
            // Deserialización ultra-rápida
            HtmlComponent comp = (HtmlComponent) conf.asObject(data);
            
            // Leemos versión e inyectamos
         // En RedisStateStore.java
            byte[] verBytes = jedis.hget(key(sessionId, path), "v".getBytes());
            if (verBytes != null) {
                comp._setVersion(Long.parseLong(new String(verBytes)));
            }
            
            return comp;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void put(String sessionId, String path, HtmlComponent component) {
        try (Jedis jedis = redisPool.getResource()) {
            // 1. Serialización
            byte[] data = conf.asByteArray(component);
            byte[] pageKey = key(sessionId, path);
            String idxKey = indexKey(sessionId);

            // 2. Pipeline para eficiencia (1 round-trip en lugar de 3)
            Pipeline p = jedis.pipelined();
            
            // Guardar la página con TTL
            p.setex(pageKey, TTL_SECONDS, data);
            
            // Añadir la ruta al índice de la sesión
            p.sadd(idxKey, path);
            
            // Renovar el TTL del índice también (para que no muera antes que las páginas)
            p.expire(idxKey, TTL_SECONDS);
            
            p.sync();
        }
    }

    @Override
    public void remove(String sessionId, String path) {
        try (Jedis jedis = redisPool.getResource()) {
            byte[] pageKey = key(sessionId, path);
            String idxKey = indexKey(sessionId);
            
            Pipeline p = jedis.pipelined();
            p.del(pageKey);
            p.srem(idxKey, path); // Lo sacamos del índice
            p.sync();
        }
    }

    @Override
    public void removeSession(String sessionId) {
        try (Jedis jedis = redisPool.getResource()) {
            String idxKey = indexKey(sessionId);
            
            // 1. Obtener todas las rutas activas de esta sesión
            Set<String> paths = jedis.smembers(idxKey);
            
            if (paths != null && !paths.isEmpty()) {
                Pipeline p = jedis.pipelined();
                
                // 2. Borrar cada página individual
                for (String path : paths) {
                    p.del(key(sessionId, path));
                }
                
                // 3. Borrar el índice mismo
                p.del(idxKey);
                
                p.sync();
            } else {
                // Si no había índice, asegurar borrado del índice por si acaso
                jedis.del(idxKey);
            }
        } catch (Exception e) {
            System.err.println("Error limpiando sesión Redis: " + e.getMessage());
        }
    }

    @Override
    public boolean replace(String sid, String path, HtmlComponent comp, long expectVer) {
        try (Jedis jedis = redisPool.getResource()) {
            byte[] key = key(sid, path); // Asegúrate que tu método key devuelva byte[]
            byte[] data = conf.asByteArray(comp);
            
            // Jedis eval requiere claves y argumentos
            Object res = jedis.eval(CAS_SCRIPT.getBytes(), 
                                    1, key, 
                                    String.valueOf(expectVer).getBytes(), 
                                    data);
            
            long result = (Long) res;
            if (result == 1) {
                // Actualizamos la versión del objeto Java local para que coincida
                comp._setVersion(expectVer + 1);
                return true;
            }
            return false;
        }
    }
}