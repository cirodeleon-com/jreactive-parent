package com.ciro.jreactive;

import org.springframework.beans.factory.annotation.Autowired;
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
	
	@Autowired ObjectMapper mapper;
	
	@PostConstruct
    public void installEngine() {
        AstComponentEngine.installAsDefault();
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
    public JrxHubManager jrxHubManager(
    		PageResolver pageResolver, 
    		ObjectMapper mapper,
    		@Autowired(required = false) com.ciro.jreactive.spi.JrxMessageBroker broker) {
        return new JrxHubManager(pageResolver, mapper,broker);
    }

}