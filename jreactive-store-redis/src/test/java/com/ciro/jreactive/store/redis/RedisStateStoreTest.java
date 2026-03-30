package com.ciro.jreactive.store.redis;

import com.ciro.jreactive.HtmlComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;

import java.lang.reflect.Field;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisStateStore - Pruebas con Redis Fantasma (Mockito)")
class RedisStateStoreTest { // <-- Mismo nombre del archivo que ya tienes

    @Mock JedisPool jedisPool;
    @Mock Jedis jedis;
    @Mock Pipeline pipeline;

    private RedisStateStore store;
    private FstStateSerializer serializer;

    // Componente de prueba
    static class MockComp extends HtmlComponent {
        @Override protected String template() { return "mock"; }
    }

    @BeforeEach
    void setUp() throws Exception {
        serializer = new FstStateSerializer();
        // Instanciamos normal
        store = new RedisStateStore("localhost", 9999, serializer);
        
        // 🔥 CIRUGÍA: Reemplazamos el pool real por nuestro fantasma
        Field poolField = RedisStateStore.class.getDeclaredField("redisPool");
        poolField.setAccessible(true);
        poolField.set(store, jedisPool);

        // Cuando el store pida una conexión, le damos nuestro Jedis fantasma
        lenient().when(jedisPool.getResource()).thenReturn(jedis);
        
        // Cuando pida un pipeline, le damos nuestro Pipeline fantasma
        lenient().when(jedis.pipelined()).thenReturn(pipeline);
    }

    @Test
    @DisplayName("Debe leer de Redis y deserializar correctamente (GET)")
    void testGetSuccess() {
        MockComp comp = new MockComp();
        comp._setVersion(5L);
        byte[] serialized = serializer.serialize(comp);

        // Simulamos que Redis devuelve los bytes y la versión
        when(jedis.hget(any(byte[].class), eq("data".getBytes()))).thenReturn(serialized);
        when(jedis.hget(any(byte[].class), eq("v".getBytes()))).thenReturn("5".getBytes());

        HtmlComponent result = store.get("sesion1", "/home");

        assertThat(result).isNotNull();
        assertThat(result._getVersion()).isEqualTo(5L);
    }

    @Test
    @DisplayName("Debe guardar en Redis usando un Pipeline (PUT)")
    void testPutSuccess() {
        MockComp comp = new MockComp();
        
        store.put("sesion1", "/home", comp);

        // Verificamos que el código ejecutó los comandos del pipeline
        verify(pipeline).setex(any(byte[].class), eq(1800L), any(byte[].class));
        verify(pipeline).sadd(anyString(), eq("/home"));
        verify(pipeline).expire(anyString(), eq(1800L));
        verify(pipeline).sync();
    }

    @Test
    @DisplayName("Debe borrar de Redis usando Pipeline (REMOVE)")
    void testRemoveSuccess() {
        store.remove("sesion1", "/home");

        verify(pipeline).del(any(byte[].class));
        verify(pipeline).srem(anyString(), eq("/home"));
        verify(pipeline).sync();
    }

    @Test
    @DisplayName("Debe borrar toda la sesión (REMOVE_SESSION)")
    void testRemoveSessionSuccess() {
        // Simulamos que el índice tiene 2 rutas guardadas
        when(jedis.smembers(anyString())).thenReturn(Set.of("/home", "/perfil"));

        store.removeSession("sesion1");

        verify(pipeline, times(2)).del(any(byte[].class)); // Debe borrar las 2 páginas
        verify(pipeline).del(anyString()); // Debe borrar el índice
        verify(pipeline).sync();
    }

    @Test
    @DisplayName("Debe ejecutar el script LUA de Replace y manejar éxito (REPLACE)")
    void testReplaceSuccess() {
        MockComp comp = new MockComp();
        
        // Simulamos que el script de Lua se ejecuta y devuelve 1 (Éxito)
        when(jedis.eval(any(byte[].class), eq(1), any(byte[].class), any(byte[].class), any(byte[].class)))
            .thenReturn(1L);

        boolean result = store.replace("sesion1", "/home", comp, 2L);

        assertThat(result).isTrue();
        assertThat(comp._getVersion()).isEqualTo(3L); // Debería incrementar la versión automáticamente
    }
}