package com.ciro.jreactive;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra un HttpSessionListener que limpia todas las páginas JReactive
 * asociadas a una sesión cuando ésta se destruye.
 */
@Configuration
public class SessionCleanupConfig {

    @Bean
    public ServletListenerRegistrationBean<HttpSessionListener> httpSessionListener(PageResolver pageResolver) {
        HttpSessionListener listener = new HttpSessionListener() {

            @Override
            public void sessionCreated(HttpSessionEvent se) {
                // aquí no necesitamos hacer nada
            }

            @Override
            public void sessionDestroyed(HttpSessionEvent se) {
                String sessionId = se.getSession().getId();
                // 🔥 limpiamos TODO lo asociado a esa sesión
                pageResolver.evictAll(sessionId);
            }
        };

        ServletListenerRegistrationBean<HttpSessionListener> bean =
                new ServletListenerRegistrationBean<>(listener);
        // opcional: orden si tienes más listeners
        bean.setOrder(0);
        return bean;
    }
}
