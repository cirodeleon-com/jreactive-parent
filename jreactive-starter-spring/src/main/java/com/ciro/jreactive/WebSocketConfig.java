package com.ciro.jreactive;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final DelegatingWebSocketHandler delegator;

    public WebSocketConfig(DelegatingWebSocketHandler delegator) {
        this.delegator = delegator;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 🔥 CAMBIO CLAVE:
        registry.addHandler(delegator, "/jrx") // Cambiamos ruta base a /jrx
                .addInterceptors(new PathInterceptor())
                .setAllowedOriginPatterns("*") // SockJS requiere OriginPatterns si usas credentials
                .withSockJS() // <--- ¡AQUÍ ESTÁ LA MAGIA!
                .setSessionCookieNeeded(true) // VITAL para Sticky Sessions (Cluster)
                //.setClientLibraryUrl("https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js")
                ; 
    }
}


