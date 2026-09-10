package com.ciro.jreactive.store.redis;

import com.ciro.jreactive.HtmlComponent;
import com.ciro.jreactive.store.StateStore;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;

import java.util.Set;

public class RedisStateStore implements StateStore {

    private final JedisPool redisPool;
    private final StateSerializer serializer; // 🔥 Ahora usamos la interfaz
    
    private static final int TTL_SECONDS = 1800; // 30 minutos
    
    // Script Lua para CAS (Compare-And-Swap) optimista
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

    // ✅ Constructor Inyectado: Recibe la estrategia (JSON o FST) desde la configuración
    public RedisStateStore(String host, int port, StateSerializer serializer) {
        this.redisPool = new JedisPool(host, port);
        this.serializer = serializer;
    }
    
    // Helper para logs de arranque
    public String getSerializerName() {
        return serializer.name();
    }

    private byte[] key(String sid, String path) {
        return ("jrx:page:" + sid + ":" + path).getBytes();
    }
    
    private String indexKey(String sid) {
        return "jrx:idx:" + sid;
    }

    @Override
    public HtmlComponent get(String sessionId, String path) {
        try (Jedis jedis = redisPool.getResource()) {
            byte[] data = jedis.hget(key(sessionId, path), "data".getBytes());
            if (data == null) return null;
            
            // 🔥 CAMBIO CLAVE: Delegamos la deserialización a la estrategia
            HtmlComponent comp = serializer.deserialize(data, HtmlComponent.class);
            
            // Leemos versión e inyectamos (La versión siempre es texto plano en Redis)
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
            // 🔥 CAMBIO CLAVE: Serialización agnóstica
            byte[] data = serializer.serialize(component);

            byte[] pageKey = key(sessionId, path);
            String idxKey = indexKey(sessionId);

            Pipeline p = jedis.pipelined();
            // PUT reemplaza el estado completo. DEL también normaliza claves STRING
            // creadas por versiones anteriores, antes de escribir el esquema HASH.
            p.del(pageKey);
            p.hset(pageKey, "data".getBytes(), data);
            p.hset(pageKey, "v".getBytes(), String.valueOf(component._getVersion()).getBytes());
            p.expire(pageKey, TTL_SECONDS);
            p.sadd(idxKey, path);
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
            p.srem(idxKey, path); 
            p.sync();
        }
    }

    @Override
    public void removeSession(String sessionId) {
        try (Jedis jedis = redisPool.getResource()) {
            String idxKey = indexKey(sessionId);
            Set<String> paths = jedis.smembers(idxKey);
            
            if (paths != null && !paths.isEmpty()) {
                Pipeline p = jedis.pipelined();
                for (String path : paths) {
                    p.del(key(sessionId, path));
                }
                p.del(idxKey);
                p.sync();
            } else {
                jedis.del(idxKey);
            }
        } catch (Exception e) {
            System.err.println("Error limpiando sesión Redis: " + e.getMessage());
        }
    }

    @Override
    public boolean replace(String sid, String path, HtmlComponent comp, long expectVer) {
        try (Jedis jedis = redisPool.getResource()) {
            byte[] key = key(sid, path);
            
            // 🔥 CAMBIO CLAVE: Serialización agnóstica para el CAS
            byte[] data = serializer.serialize(comp);
            
            Object res = jedis.eval(CAS_SCRIPT.getBytes(), 
                                    1, key, 
                                    String.valueOf(expectVer).getBytes(), 
                                    data);
            
            long result = (Long) res;
            if (result == 1) {
                comp._setVersion(expectVer + 1);
                return true;
            }
            return false;
        }
    }
}