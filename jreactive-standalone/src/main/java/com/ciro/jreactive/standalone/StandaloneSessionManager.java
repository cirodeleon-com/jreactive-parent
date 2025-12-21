/* === File: jreactive-standalone/src/main/java/com/ciro/jreactive/standalone/StandaloneSessionManager.java === */
package com.ciro.jreactive.standalone;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StandaloneSessionManager {

    public static final String COOKIE_NAME = "JRXID";
    private static final SecureRandom RNG = new SecureRandom();

    private final Map<String, SessionData> sessions = new ConcurrentHashMap<>();

    public static final class SessionData {
        public final String id;
        public volatile String lastPath = "/";

        SessionData(String id) {
            this.id = id;
        }
    }

    public SessionData ensureSession(HttpServerExchange exchange) {
        String sid = CookieUtil.getCookie(exchange, COOKIE_NAME);
        if (sid == null || sid.isBlank()) {
            sid = newId();
            CookieUtil.setCookie(exchange, COOKIE_NAME, sid);
        }

        SessionData data = sessions.computeIfAbsent(sid, SessionData::new);

        // disponibilidad en attachment por request
        exchange.putAttachment(UndertowAttachments.SESSION_ID, sid);

        return data;
    }

    public String getSessionId(HttpServerExchange exchange) {
        String sid = exchange.getAttachment(UndertowAttachments.SESSION_ID);
        if (sid != null) return sid;

        SessionData data = ensureSession(exchange);
        return data.id;
    }

    public void setLastPath(String sessionId, String path) {
        if (sessionId == null) return;
        SessionData data = sessions.computeIfAbsent(sessionId, SessionData::new);
        data.lastPath = (path == null || path.isBlank()) ? "/" : path;
    }

    public String getLastPath(String sessionId) {
        if (sessionId == null) return "/";
        SessionData data = sessions.get(sessionId);
        return data != null ? (data.lastPath != null ? data.lastPath : "/") : "/";
    }

    public void touchNoCache(HttpServerExchange exchange) {
        exchange.getResponseHeaders().put(Headers.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0");
        exchange.getResponseHeaders().put(Headers.PRAGMA, "no-cache");
    }

    private static String newId() {
        byte[] b = new byte[18];
        RNG.nextBytes(b);
        return Base64Url.encode(b);
    }
}
