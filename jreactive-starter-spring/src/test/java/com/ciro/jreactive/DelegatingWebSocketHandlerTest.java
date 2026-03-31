package com.ciro.jreactive;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("DelegatingWebSocketHandler - Conexión de WebSockets")
class DelegatingWebSocketHandlerTest {

    @Test
    @DisplayName("Debe delegar correctamente los eventos y limpiar la sesión al cerrar")
    void shouldDelegateEventsAndCleanSession() throws Exception {
        // Arrange
        PageResolver pageResolver = mock(PageResolver.class);
        ObjectMapper mapper = new ObjectMapper();
        
        WsConfig wsConfig = new WsConfig();
        // Configuramos el config para que permita el desalojo (evict)
        wsConfig.setPersistentState(false); 
        
        JrxHubManager hubManager = mock(JrxHubManager.class);

        HtmlComponent mockPage = mock(HtmlComponent.class);
        // Evitar NullPointer interno simulando el _state()
        when(mockPage._state()).thenReturn(ComponentState.MOUNTED);
        
        when(pageResolver.getPage(anyString(), anyString())).thenReturn(mockPage);

        DelegatingWebSocketHandler handler = new DelegatingWebSocketHandler(
                pageResolver, mapper, Executors.newSingleThreadScheduledExecutor(), wsConfig, hubManager
        );

        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        
        // 🔥 EL FIX: Pre-cargamos los atributos críticos que el PathInterceptor dejaría
        attributes.put("path", "/");
        attributes.put("sessionId", "ws-123");
        
        when(session.getAttributes()).thenReturn(attributes);
        when(session.getId()).thenReturn("ws-123");

        // 1. Simular conexión
        handler.afterConnectionEstablished(session);
        
        assertThat(attributes).containsKey("delegate");
        assertThat(attributes).containsKey("isStatefulRam");

        // 2. Simular mensaje
        TextMessage msg = new TextMessage("{\"k\":\"test\"}");
        handler.handleMessage(session, msg); // Solo verificamos que no lance excepción

        // 3. Simular error
        handler.handleTransportError(session, new RuntimeException("test error"));

        // 4. Simular cierre (Código 1000 = Limpieza forzada según tu lógica)
        handler.afterConnectionClosed(session, CloseStatus.NORMAL); // NORMAL es 1000
        
        // Assert: Ahora sí debería llamar a evict
        verify(pageResolver, times(1)).evict("ws-123", "/");
        verify(hubManager, times(1)).evict("ws-123", "/");
    }
}