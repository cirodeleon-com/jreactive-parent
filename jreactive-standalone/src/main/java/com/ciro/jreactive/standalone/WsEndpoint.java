package com.ciro.jreactive.standalone;

import com.ciro.jreactive.HtmlComponent;
import com.ciro.jreactive.ComponentState; // Asegúrate de importar esto
import com.ciro.jreactive.JrxHubManager;
import com.ciro.jreactive.JrxProtocolHandler;
import com.ciro.jreactive.JrxPushHub;
import com.ciro.jreactive.PageResolver;
import com.ciro.jreactive.spi.JrxSession; // Usamos la interfaz del SPI
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final JrxHubManager hubManager;

    public WsEndpoint(PageResolver pageResolver,
                      ObjectMapper mapper,
                      ScheduledExecutorService scheduler,
                      StandaloneSessionManager sessionManager,
                      JrxHubManager hubManager) {
        this.pageResolver = pageResolver;
        this.mapper = mapper;
        this.scheduler = scheduler;
        this.sessionManager = sessionManager;
        this.hubManager = hubManager;
    }

    public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {
        String path = getQueryParam(exchange, "path");
        if (path == null || path.isBlank()) path = "/";

        long since = 0;
        String sinceParam = getQueryParam(exchange, "since");
        if (sinceParam != null) {
            try {
                since = Long.parseLong(sinceParam);
            } catch (NumberFormatException e) {
                // ignorar
            }
        }

        String sessionId = getCookieFromHandshake(exchange, StandaloneSessionManager.COOKIE_NAME);
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "WS@" + channel.getSourceAddress() + "@" + System.identityHashCode(channel);
        }
        final String sid = sessionId;

        sessionManager.setLastPath(sessionId, path);

        // Usamos la implementación concreta para Undertow, pero referenciada como interfaz JrxSession
        JrxSession session = new UndertowJrxSession(channel, sessionId);

        System.out.println("🔌 WS CONNECT sid=" + sid + " path=" + path + " since=" + since);

        try {
            HtmlComponent page = pageResolver.getPage(sid, path);

            // Resurrección si viene de Redis
            if (page._state() == ComponentState.UNMOUNTED) {
                page._initIfNeeded();
                page._mountRecursive();
            }

            JrxPushHub hub = hubManager.hub(sid, path);
            
            final String finalPath = path;

            JrxProtocolHandler handler = new JrxProtocolHandler(
                page,
                mapper,
                scheduler,
                true, // Backpressure activado
                512,  // Max queue
                16,   // Flush interval
                () -> {
                    try {
                        pageResolver.persist(sid, finalPath, page);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            );

            // Pasamos la sesión genérica
            handler.onOpen(session, hub, since);

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