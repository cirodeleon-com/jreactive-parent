package com.ciro.jreactive;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final JReactiveSocketHandler socketHandler;

    public WebSocketConfig(JReactiveApplication app) {
    	HtmlComponent root = app.getRoot();//registry.resolve("/");
        this.socketHandler = new JReactiveSocketHandler(root);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(socketHandler, "/ws").setAllowedOrigins("*");
    }
}
