package com.ciro.jreactive;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ciro.jreactive.router.RouteProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;

@Configuration
public class JReactiveAutoConfiguration {

    @Bean
    public CallGuard callGuard(Validator validator, ObjectMapper mapper) {
        return new CallGuard(validator, mapper);
    }
    
    @Bean
    public PageResolver pageResolver(RouteProvider routeProvider) {
        return new PageResolver(routeProvider);
    }
}