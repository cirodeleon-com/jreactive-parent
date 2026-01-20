package com.ciro.jreactive.store.redis;

import com.ciro.jreactive.spi.JrxMessageBroker;
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

    // 👇 Nueva propiedad: 'strong' (default) o 'eventual'
    @Value("${jreactive.store.consistency:strong}")
    private String consistencyMode;

    @Bean
    public RedisStateStore redisStateStore() {
        return new RedisStateStore(host, port);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "jreactive.store.type", havingValue = "redis")
    public StateStore onlyRedis(RedisStateStore redisStore) {
        return redisStore;
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "jreactive.store.type", havingValue = "hybrid")
    public StateStore hybridStore(RedisStateStore redisStore) {
        CaffeineStateStore l1 = new CaffeineStateStore();
        
        // Detectar modo
        boolean isStrong = "strong".equalsIgnoreCase(consistencyMode);
        
        System.out.println("🚀 JReactive Hybrid Store iniciado. Modo consistencia: " + 
                           (isStrong ? "STRONG (Enterprise)" : "EVENTUAL (Speed)"));

        return new HybridStateStore(l1, redisStore, isStrong);
    }
    
    @Bean
    @ConditionalOnProperty(name = "jreactive.store.type", havingValue = "hybrid")
    public JrxMessageBroker redisMessageBroker() {
        return new RedisMessageBroker(host, port);
    }
}