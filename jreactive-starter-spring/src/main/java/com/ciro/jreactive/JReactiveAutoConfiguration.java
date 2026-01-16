package com.ciro.jreactive;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ciro.jreactive.router.RouteProvider;
import com.ciro.jreactive.store.CaffeineStateStore;
import com.ciro.jreactive.store.StateStore;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.validation.Validator;

@Configuration
public class JReactiveAutoConfiguration {
	
	@PostConstruct
    public void installEngine() {
        JsoupComponentEngine.installAsDefault();
    }
	
	@Bean
    @ConditionalOnMissingBean(StateStore.class)
    public StateStore stateStore() {
        return new CaffeineStateStore();
    }

    @Bean
    public CallGuard callGuard(Validator validator, ObjectMapper mapper) {
        return new CallGuard(validator, mapper);
    }
    
    @Bean
    public PageResolver pageResolver(RouteProvider routeProvider, StateStore store) {
        return new PageResolver(routeProvider, store);
    }
    
    @Bean
    public JrxHubManager jrxHubManager(PageResolver pageResolver, ObjectMapper mapper) {
        return new JrxHubManager(pageResolver, mapper);
    }

}