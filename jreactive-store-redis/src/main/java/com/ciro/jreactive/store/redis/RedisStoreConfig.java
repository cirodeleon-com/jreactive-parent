package com.ciro.jreactive.store.redis;

import com.ciro.jreactive.store.CaffeineStateStore;
import com.ciro.jreactive.store.StateStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class RedisStoreConfig {

    @Value("${jreactive.redis.host:localhost}")
    private String host;

    @Value("${jreactive.redis.port:6379}")
    private int port;

    // 1. Bean base de Redis (usado por ambos)
    @Bean
    public RedisStateStore redisStateStore() {
        return new RedisStateStore(host, port);
    }

    // 2. Estrategia A: SOLO REDIS (Stateless total)
    // Se activa con: jreactive.store.type=redis
    @Bean
    @Primary // Gana sobre el Caffeine por defecto si se activa
    @ConditionalOnProperty(name = "jreactive.store.type", havingValue = "redis")
    public StateStore onlyRedis(RedisStateStore redisStore) {
        return redisStore;
    }

    // 3. Estrategia B: HÍBRIDO (RAM + Redis Async)
    // Se activa con: jreactive.store.type=hybrid
    @Bean
    @Primary
    @ConditionalOnProperty(name = "jreactive.store.type", havingValue = "hybrid")
    public StateStore hybridStore(RedisStateStore redisStore) {
        // Reutilizamos Caffeine como L1
        CaffeineStateStore l1 = new CaffeineStateStore();
        return new HybridStateStore(l1, redisStore);
    }
}