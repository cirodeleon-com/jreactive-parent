package com.ciro.jreactive;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;

import static org.mockito.Mockito.*;

@DisplayName("SessionCleanupConfig - Limpieza Automática")
class SessionCleanupConfigTest {

    @Test
    @DisplayName("Debe ejecutar evictAll cuando la sesión HTTP se destruye")
    void shouldEvictAllOnSessionDestroyed() {
        PageResolver pageResolver = mock(PageResolver.class);
        SessionCleanupConfig config = new SessionCleanupConfig();

        ServletListenerRegistrationBean<HttpSessionListener> bean = config.httpSessionListener(pageResolver);
        HttpSessionListener listener = bean.getListener();

        HttpSession session = mock(HttpSession.class);
        when(session.getId()).thenReturn("session-abc");
        HttpSessionEvent event = new HttpSessionEvent(session);

        listener.sessionCreated(event);
        verify(pageResolver, never()).evictAll(anyString());

        listener.sessionDestroyed(event);
        verify(pageResolver, times(1)).evictAll("session-abc");
    }
}