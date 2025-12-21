package com.ciro.jreactive.standalone;

import com.ciro.jreactive.HtmlComponent;
import com.ciro.jreactive.JrxProtocolHandler;
import com.ciro.jreactive.PageResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.spi.WebSocketHttpExchange;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

public class WsEndpoint {

    private final PageResolver pageResolver;
    private final ObjectMapper mapper;
    private final ScheduledExecutorService scheduler;
    private final StandaloneSessionManager sessionManager;

    public WsEndpoint(PageResolver pageResolver,
                      ObjectMapper mapper,
                      ScheduledExecutorService scheduler,
                      StandaloneSessionManager sessionManager) {
        this.pageResolver = pageResolver;
        this.mapper = mapper;
        this.scheduler = scheduler;
        this.sessionManager = sessionManager;
    }

    public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {
        String path = getQueryParam(exchange, "path");
        if (path == null || path.isBlank()) path = "/";

        // Sesión estable: cookie JRXID
        String sessionId = getCookieFromHandshake(exchange, StandaloneSessionManager.COOKIE_NAME);
        if (sessionId == null || sessionId.isBlank()) {
            // fallback extremo (debería existir porque el HTML setea cookie)
            sessionId = "WS@" + channel.getSourceAddress() + "@" + System.identityHashCode(channel);
        }
        final String sid = sessionId;


        sessionManager.setLastPath(sessionId, path);

        UndertowJrxSession session = new UndertowJrxSession(channel, sessionId);

        System.out.println("🔌 WS CONNECT sid=" + sid + " path=" + path);

        try {
            HtmlComponent page = pageResolver.getPage(sid, path);

            // Backpressure / batching (ajusta si quieres)
            JrxProtocolHandler handler = new JrxProtocolHandler(
                page,
                mapper,
                scheduler,
                true,
                512,
                16
            );

            handler.onOpen(session);

            channel.getReceiveSetter().set(new AbstractReceiveListener() {
                @Override
                protected void onFullTextMessage(WebSocketChannel ch, BufferedTextMessage message) {
                    handler.onMessage(session, message.getData());
                }
            });

            channel.getCloseSetter().set(ch -> {
                handler.onClose(session);
                System.out.println("❌ WS CLOSE sid=" + sid);
            });

            channel.resumeReceives();

        } catch (Exception e) {
            e.printStackTrace();
            try { channel.close(); } catch (Exception ignored) {}
        }
    }

    private static String getQueryParam(WebSocketHttpExchange exchange, String key) {
        Map<String, List<String>> params = exchange.getRequestParameters();
        if (params == null) return null;
        List<String> v = params.get(key);
        return (v == null || v.isEmpty()) ? null : v.get(0);
    }

    private static String getCookieFromHandshake(WebSocketHttpExchange exchange, String name) {
        String cookieHeader = exchange.getRequestHeader("Cookie");
        if (cookieHeader == null) return null;
        String[] parts = cookieHeader.split(";");
        for (String p : parts) {
            String s = p.trim();
            int idx = s.indexOf('=');
            if (idx <= 0) continue;
            String k = s.substring(0, idx).trim();
            String v = s.substring(idx + 1).trim();
            if (name.equals(k)) return v;
        }
        return null;
    }
}
