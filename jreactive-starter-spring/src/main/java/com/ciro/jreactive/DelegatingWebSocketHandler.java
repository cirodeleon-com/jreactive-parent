package com.ciro.jreactive;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

@Component
public class DelegatingWebSocketHandler implements WebSocketHandler {

    private final PageResolver pageResolver;
    private final ObjectMapper mapper;
    private final ScheduledExecutorService scheduler;
    private final WsConfig wsConfig;
    // 👇 Nuevo
    private final JrxHubManager hubManager; 

    public DelegatingWebSocketHandler(PageResolver pageResolver,
                                      ObjectMapper mapper,
                                      ScheduledExecutorService scheduler,
                                      WsConfig wsConfig,
                                      JrxHubManager hubManager) { // <--- Inyectado por Spring
        this.pageResolver = pageResolver;
        this.mapper = mapper;
        this.scheduler = scheduler;
        this.wsConfig = wsConfig;
        this.hubManager = hubManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String path = (String) session.getAttributes().get("path");
        if (path == null) path = "/";

        String sessionId = (String) session.getAttributes().get("sessionId");
        if (sessionId == null) {
            sessionId = session.getId();
            session.getAttributes().put("sessionId", sessionId);
        }

        HtmlComponent page = pageResolver.getPage(sessionId, path);

        // 🔥 CAMBIO: Ahora pasamos hubManager, path y sessionId
        JReactiveSocketHandler delegate = new JReactiveSocketHandler(
            page, mapper, scheduler, wsConfig,
            hubManager, path, sessionId
        );
        
        delegate.afterConnectionEstablished(session);

        session.getAttributes().put("delegate", delegate);
    }

    // ... (El resto de métodos handleMessage, handleTransportError, etc. QUEDAN IGUALES) ...
    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        var delegate = (WebSocketHandler) session.getAttributes().get("delegate");
        if (delegate != null) delegate.handleMessage(session, message);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        var delegate = (WebSocketHandler) session.getAttributes().get("delegate");
        if (delegate != null) delegate.handleTransportError(session, exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        var delegate = (WebSocketHandler) session.getAttributes().get("delegate");
        if (delegate != null) delegate.afterConnectionClosed(session, status);

        String path = (String) session.getAttributes().get("path");
        String sessionId = (String) session.getAttributes().get("sessionId");

        if (path != null && sessionId != null  && "route-change".equals(status.getReason())) {
            pageResolver.evict(sessionId, path);
        }
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}