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
        registry.addHandler(delegator, "/ws")
                .setAllowedOrigins("*")
                .addInterceptors(new PathInterceptor());
    }
}


