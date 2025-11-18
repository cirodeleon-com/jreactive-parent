package com.ciro.jreactive;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class WsResourcesConfig {
	
    @Bean(destroyMethod = "shutdownNow")
    ScheduledExecutorService jreactiveExecutor() {
        return Executors.newSingleThreadScheduledExecutor();
    }
    
}

