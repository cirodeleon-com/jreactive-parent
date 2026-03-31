package com.ciro.jreactive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PathInterceptor - Pruebas de Handshake")
class PathInterceptorTest {

    @Test
    @DisplayName("Debe extraer el path, since y sessionId del HttpServletRequest")
    void shouldExtractAttributesFromRequest() {
        // Arrange
        PathInterceptor interceptor = new PathInterceptor();
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setParameter("path", "/dashboard");
        mockRequest.setParameter("since", "1024");
        
        // Forzamos la creación de una sesión simulada
        String expectedSessionId = mockRequest.getSession(true).getId();
        
        ServletServerHttpRequest serverRequest = new ServletServerHttpRequest(mockRequest);
        Map<String, Object> attributes = new HashMap<>();

        // Act
        boolean result = interceptor.beforeHandshake(serverRequest, null, null, attributes);

        // Assert
        assertThat(result).isTrue();
        assertThat(attributes)
            .containsEntry("path", "/dashboard")
            .containsEntry("since", "1024")
            .containsEntry("sessionId", expectedSessionId);
    }

    @Test
    @DisplayName("Debe usar valores por defecto si los parámetros no vienen en la URL")
    void shouldUseDefaultValuesWhenMissing() {
        // Arrange
        PathInterceptor interceptor = new PathInterceptor();
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        ServletServerHttpRequest serverRequest = new ServletServerHttpRequest(mockRequest);
        Map<String, Object> attributes = new HashMap<>();

        // Act
        interceptor.beforeHandshake(serverRequest, null, null, attributes);

        // Assert
        assertThat(attributes)
            .containsEntry("path", "/")
            .containsEntry("since", "0")
            .containsKey("sessionId");
    }
}