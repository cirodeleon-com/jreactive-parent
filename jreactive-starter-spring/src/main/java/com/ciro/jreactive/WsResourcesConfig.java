package com.ciro.jreactive;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WsResourcesConfig {
	
    @Bean(destroyMethod = "shutdownNow")
    ScheduledExecutorService jreactiveExecutor() {
        // Obtenemos el número de procesadores disponibles (ej: 4, 8, 16)
        int cores = Runtime.getRuntime().availableProcessors();
        
        // Creamos un pool programado que permite ejecutar tareas en paralelo.
        // Esto evita que un flush lento de un usuario bloquee a los demás.
        return Executors.newScheduledThreadPool(cores);
    }
}