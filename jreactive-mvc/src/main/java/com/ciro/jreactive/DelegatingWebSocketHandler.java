package com.ciro.jreactive;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

@Component
public class DelegatingWebSocketHandler implements WebSocketHandler {

    private final PageResolver pageResolver;
    private final ObjectMapper mapper;

    public DelegatingWebSocketHandler(PageResolver pageResolver,ObjectMapper mapper) {
        this.pageResolver = pageResolver;
        this.mapper = mapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String path = (String) session.getAttributes().get("path");
        if (path == null) path = "/";
        HtmlComponent page = pageResolver.getPage(path);

        JReactiveSocketHandler delegate = new JReactiveSocketHandler(page,mapper);
        delegate.afterConnectionEstablished(session);

        // OPCIONAL: podrías guardar `session -> delegate` si quieres manejar mensajes luego
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
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}

