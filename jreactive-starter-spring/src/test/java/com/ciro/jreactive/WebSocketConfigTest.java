package com.ciro.jreactive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.config.annotation.SockJsServiceRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("WebSocketConfig - Registro de Handlers WS")
class WebSocketConfigTest {

    @Test
    @DisplayName("Debe registrar el handler principal en la ruta /jrx y activar SockJS")
    void shouldRegisterHandlersAndSockJs() {
        // Arrange
        DelegatingWebSocketHandler handler = mock(DelegatingWebSocketHandler.class);
        WebSocketConfig config = new WebSocketConfig(handler);

        WebSocketHandlerRegistry registry = mock(WebSocketHandlerRegistry.class);
        WebSocketHandlerRegistration registration = mock(WebSocketHandlerRegistration.class);
        SockJsServiceRegistration sockJsRegistration = mock(SockJsServiceRegistration.class);

        when(registry.addHandler(any(), eq("/jrx"))).thenReturn(registration);
        when(registration.addInterceptors(any())).thenReturn(registration);
        when(registration.setAllowedOriginPatterns(any())).thenReturn(registration);
        when(registration.withSockJS()).thenReturn(sockJsRegistration);
        when(sockJsRegistration.setSessionCookieNeeded(true)).thenReturn(sockJsRegistration);

        // Act
        config.registerWebSocketHandlers(registry);

        // Assert
        verify(registry).addHandler(handler, "/jrx");
        verify(registration).addInterceptors(any(PathInterceptor.class));
        verify(registration).setAllowedOriginPatterns("*");
        verify(registration).withSockJS();
        verify(sockJsRegistration).setSessionCookieNeeded(true);
    }
}