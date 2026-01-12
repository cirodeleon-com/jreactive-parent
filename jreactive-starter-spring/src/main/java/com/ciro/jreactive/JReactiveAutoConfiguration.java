package com.ciro.jreactive;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ciro.jreactive.router.RouteProvider;
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
    public CallGuard callGuard(Validator validator, ObjectMapper mapper) {
        return new CallGuard(validator, mapper);
    }
    
    @Bean
    public PageResolver pageResolver(RouteProvider routeProvider) {
        return new PageResolver(routeProvider);
    }
    
    @Bean
    public JrxHubManager jrxHubManager(PageResolver pageResolver, ObjectMapper mapper) {
        return new JrxHubManager(pageResolver, mapper);
    }

}