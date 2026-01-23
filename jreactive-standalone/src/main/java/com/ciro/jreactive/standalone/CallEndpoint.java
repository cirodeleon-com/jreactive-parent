package com.ciro.jreactive.standalone;

import com.ciro.jreactive.CallGuard;
import com.ciro.jreactive.JrxHttpApi;
import com.ciro.jreactive.PageResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Standalone adapter: /call/{method}
 *
 * FIX Undertow: usar receiveFullBytes().
 * FIX Session: usar cookie JRXID (StandaloneSessionManager.COOKIE_NAME) y fallback a sessions.getLastPath(sid).
 *
 * Delegamos la lógica real de invocación a JrxHttpApi (runtime-jvm).
 */
public final class CallEndpoint implements HttpHandler {

    private final ObjectMapper objectMapper;
    private final StandaloneSessionManager sessions;
    private final JrxHttpApi api;

    public CallEndpoint(PageResolver pageResolver,
                        CallGuard callGuard,
                        ObjectMapper objectMapper,
                        StandaloneSessionManager sessions) {
        this.objectMapper = objectMapper;
        this.sessions = sessions;
        this.api = new JrxHttpApi(pageResolver, objectMapper, callGuard,true);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) {

        if (!exchange.getRequestMethod().equalToString("POST")) {
            exchange.setStatusCode(405);
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send("{\"ok\":false,\"code\":\"METHOD_NOT_ALLOWED\",\"error\":\"Only POST is allowed\"}");
            return;
        }

        final String callName = extractCallName(exchange.getRequestPath());
        if (callName == null || callName.isBlank()) {
            exchange.setStatusCode(404);
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send("{\"ok\":false,\"code\":\"NOT_FOUND\",\"error\":\"Call not found\"}");
            return;
        }

        // ✅ FIX Undertow: Leer body correctamente
        exchange.getRequestReceiver().receiveFullBytes((ex, bytes) -> {
            try {
                // ✅ Asegura sesión (crea cookie JRXID si no existe)
                sessions.ensureSession(ex);

                Map<String, Object> body = parseBody(bytes);

                // ✅ SID REAL (cookie JRXID) — NO "sid"
                String sid = sessions.getSessionId(ex);

                // ✅ PATH: si viene en body/header úsalo; si no, usa lastPath de la sesión
                String path = firstNonBlank(
                        asString(body.get("path")),
                        asString(body.get("route")),
                        header(ex, "x-jrx-path"),
                        sessions.getLastPath(sid),
                        "/"
                );

                // Mantén lastPath actualizado
                sessions.setLastPath(sid, path);

                String json = api.call(sid, path, callName, body);

                ex.setStatusCode(200);
                ex.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                ex.getResponseSender().send(json);

            } catch (Exception e) {
                ex.setStatusCode(500);
                ex.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                ex.getResponseSender().send("{\"ok\":false,\"code\":\"INTERNAL\",\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }, (ex, err) -> {
            ex.setStatusCode(400);
            ex.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            ex.getResponseSender().send("{\"ok\":false,\"code\":\"BAD_REQUEST\",\"error\":\"" + escapeJson(err.getMessage()) + "\"}");
        });
    }

    private Map<String, Object> parseBody(byte[] bytes) throws Exception {
        if (bytes == null || bytes.length == 0) return Map.of();
        String raw = new String(bytes, StandardCharsets.UTF_8).trim();
        if (raw.isEmpty()) return Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> map = objectMapper.readValue(raw, Map.class);
        return (map == null) ? Map.of() : map;
    }

    private static String extractCallName(String requestPath) {
        // ej: "/call/increment" -> "increment"
        if (requestPath == null) return null;
        int idx = requestPath.lastIndexOf('/');
        if (idx < 0 || idx == requestPath.length() - 1) return null;
        return requestPath.substring(idx + 1);
    }

    private static String header(HttpServerExchange ex, String name) {
        String h = ex.getRequestHeaders().getFirst(name);
        return (h == null) ? "" : h.trim();
    }

    private static String asString(Object o) {
        return (o == null) ? "" : String.valueOf(o).trim();
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
