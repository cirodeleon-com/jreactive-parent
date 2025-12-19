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
 * No contiene lógica de negocio, solo traducción.
 */
public class JReactiveSocketHandler extends TextWebSocketHandler {

    private final JrxProtocolHandler protocol;

    // Cache de wrappers para mantener la identidad (equals/hashCode) correcta
    // Usamos esto para que el mismo objeto WebSocketSession siempre devuelva el mismo JrxSession
    // y el Set<JrxSession> en el Core pueda removerlo correctamente.
    private final Map<WebSocketSession, JrxSession> wrappers = new ConcurrentHashMap<>();

    public JReactiveSocketHandler(ViewNode root,
                                  ObjectMapper mapper,
                                  ScheduledExecutorService scheduler,
                                  WsConfig cfg) {
        // Inicializamos el Core con valores primitivos
        this.protocol = new JrxProtocolHandler(
            root,
            mapper,
            scheduler,
            cfg.isEnabledBackpressure(),
            cfg.getMaxQueue(),
            cfg.getFlushIntervalMs()
        );
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        JrxSession wrapper = new SpringWsWrapper(session);
        wrappers.put(session, wrapper);
        protocol.onOpen(wrapper);
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

    /**
     * Implementación interna de la SPI usando Spring
     */
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

        // IMPORTANTE: Identidad basada en la sesión subyacente
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