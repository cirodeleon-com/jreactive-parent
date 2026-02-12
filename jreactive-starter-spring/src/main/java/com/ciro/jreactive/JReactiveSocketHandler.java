package com.ciro.jreactive;

import com.ciro.jreactive.spi.JrxSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

/**
 * ADAPTADOR: Spring WebSocket -> JReactive Core.
 * Ahora inyecta el HubManager para recuperación de historial.
 */
public class JReactiveSocketHandler extends TextWebSocketHandler {

    private final JrxProtocolHandler protocol;
    
    // 👇 Nuevos campos para contexto
    private final JrxHubManager hubManager;
    private final String path;
    private final String sessionId;
    private final PageResolver pageResolver;

    private final Map<WebSocketSession, JrxSession> wrappers = new ConcurrentHashMap<>();
    private final HtmlComponent page;

    public JReactiveSocketHandler(HtmlComponent page,
                                  ObjectMapper mapper,
                                  ScheduledExecutorService scheduler,
                                  WsConfig cfg,
                                  JrxHubManager hubManager, // <--- Nuevo param
                                  String path,              // <--- Nuevo param
                                  String sessionId,
                                  PageResolver pageResolver) {       // <--- Nuevo param
        this.hubManager = hubManager;
        this.path = path;
        this.sessionId = sessionId;
        this.pageResolver = pageResolver;
        this.page=page;
        Runnable saveStrategy = cfg.isPersistentState() 
                ? () -> this.pageResolver.persist(sessionId, path, this.page)
                : null;

        this.protocol = new JrxProtocolHandler(
            this.page,
            mapper,
            scheduler,
            cfg.isEnabledBackpressure(),
            cfg.getMaxQueue(),
            cfg.getFlushIntervalMs(),
            saveStrategy
        );
    }


    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        JrxSession wrapper = new SpringWsWrapper(session);
        wrappers.put(session, wrapper);

        // 👇 1. CORRECCIÓN PARA SOCKJS:
        // En lugar de leer session.getUri().getQuery() (que SockJS oculta),
        // leemos el atributo que PathInterceptor guardó durante el handshake HTTP.
        long since = 0;
        String sinceStr = (String) session.getAttributes().get("since");
        
        if (sinceStr != null) {
            try {
                since = Long.parseLong(sinceStr);
            } catch (NumberFormatException e) {
                // ignorar si no es un número válido
            }
        }
        
        // 🔥 FIX CRÍTICO: Asegurar que la página esté montada (Timers corriendo)
        // Esto es necesario si el usuario viene de una reconexión y el objeto estaba "dormido" en Redis
        if (page._state() == ComponentState.UNMOUNTED) {
            page._initIfNeeded();
            page._mountRecursive();
        }

        // 👇 2. Obtener el Hub para esta sesión (si existe)
        JrxPushHub hub = (hubManager != null) ? hubManager.hub(sessionId, path) : null;

        // 👇 3. Pasamos todo al protocolo para que reenvíe los mensajes perdidos
        protocol.onOpen(wrapper, hub, since);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        JrxSession wrapper = wrappers.remove(session);
        if (wrapper != null) {
            protocol.onClose(wrapper);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JrxSession wrapper = wrappers.get(session);
        if (wrapper != null) {
            protocol.onMessage(wrapper, message.getPayload());
        }
    }

    // ... (La clase interna SpringWsWrapper se queda IGUAL, no la copies si no quieres, pero aquí va completa por seguridad) ...
    private static class SpringWsWrapper implements JrxSession {
        private final WebSocketSession session;

        public SpringWsWrapper(WebSocketSession session) {
            this.session = session;
        }

        @Override
        public String getId() {
            return session.getId();
        }

        @Override
        public boolean isOpen() {
            return session.isOpen();
        }

        @Override
        public void sendText(String json) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            } catch (IOException e) {
                // Log or ignore
            }
        }

        @Override
        public void close() {
            try {
                session.close();
            } catch (IOException e) {
                // ignore
            }
        }

        @Override
        public void setAttr(String key, Object val) {
            session.getAttributes().put(key, val);
        }

        @Override
        public Object getAttr(String key) {
            return session.getAttributes().get(key);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SpringWsWrapper that = (SpringWsWrapper) o;
            return session.equals(that.session);
        }

        @Override
        public int hashCode() {
            return session.hashCode();
        }
    }
}