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

    public DelegatingWebSocketHandler(PageResolver pageResolver,
    		ObjectMapper mapper,
    		ScheduledExecutorService scheduler,
    		WsConfig wsConfig) {
    	
        this.pageResolver = pageResolver;
        this.mapper = mapper;
        this.scheduler = scheduler;
        this.wsConfig = wsConfig;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String path = (String) session.getAttributes().get("path");
        if (path == null) path = "/";

        String sessionId = (String) session.getAttributes().get("sessionId");
        if (sessionId == null) {
            // Fallback: por si acaso no vino del interceptor
            sessionId = session.getId();
            session.getAttributes().put("sessionId", sessionId);
        }

        HtmlComponent page = pageResolver.getPage(sessionId, path);

        JReactiveSocketHandler delegate = new JReactiveSocketHandler(page, mapper, scheduler, wsConfig);
        delegate.afterConnectionEstablished(session);

        // Guardamos el delegate para reenviar mensajes
        session.getAttributes().put("delegate", delegate);
    }


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

        // Sólo hacemos evict cuando cerramos por cambio de ruta,
        // y SOLO para esa sesión concreta
        if (path != null && sessionId != null /* && "route-change".equals(status.getReason())*/) {
            pageResolver.evict(sessionId, path);
        }
    }


    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}

