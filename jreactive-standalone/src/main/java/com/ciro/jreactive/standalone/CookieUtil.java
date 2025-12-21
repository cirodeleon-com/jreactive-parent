/* === File: jreactive-standalone/src/main/java/com/ciro/jreactive/standalone/CookieUtil.java === */
package com.ciro.jreactive.standalone;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

public final class CookieUtil {

    private CookieUtil() {}

    public static String getCookie(HttpServerExchange exchange, String name) {
        String cookieHeader = exchange.getRequestHeaders().getFirst(Headers.COOKIE);
        if (cookieHeader == null) return null;

        // parsing simple: "a=b; c=d"
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

    public static void setCookie(HttpServerExchange exchange, String name, String value) {
        // Cookie simple (dev). Si necesitas SameSite, Secure, etc. lo ajustamos luego.
        // Path=/ para que aplique a /ws y /call también.
        String cookie = name + "=" + value + "; Path=/; HttpOnly";
        exchange.getResponseHeaders().add(Headers.SET_COOKIE, cookie);
    }
}
