package com.ciro.jreactive.standalone;

import com.ciro.jreactive.HtmlComponent;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

public class PageEndpoint implements HttpHandler {

    private final SimpleRouteRegistry registry;
    private final StandaloneSessionManager sessionManager;

    public PageEndpoint(SimpleRouteRegistry registry,
                        StandaloneSessionManager sessionManager) {
        this.registry = registry;
        this.sessionManager = sessionManager;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {

        String sid = sessionManager.getSessionId(exchange);

        String requestPath = exchange.getRequestPath();
        if (requestPath == null || requestPath.isBlank()) requestPath = "/";

        sessionManager.touchNoCache(exchange);

        // Partial request para router SPA
        String partial = exchange.getRequestHeaders().getFirst("X-Partial");
        boolean isPartial = "1".equals(partial);

        try {
            RouteProviderResult result = RouteProviderResult.from(registry.resolve(requestPath));
            HtmlComponent component = result.component();

            // Render BODY (sin html/head) en partial
            String bodyHtml = component.render();

            if (isPartial) {
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html; charset=UTF-8");
                exchange.getResponseSender().send(bodyHtml);
                return;
            }

            // Full HTML
            String full = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>JReactive Standalone</title>
                    <script src="/js/jreactive-runtime.js"></script>
                    <style> body { margin:0; padding:0; font-family: system-ui, -apple-system, Segoe UI, sans-serif; } </style>
                </head>
                <body>
                    <div id="app">%s</div>
                </body>
                </html>
            """.formatted(bodyHtml);

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html; charset=UTF-8");
            exchange.getResponseSender().send(full);

        } catch (Exception e) {
            exchange.setStatusCode(404);
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain; charset=UTF-8");
            exchange.getResponseSender().send("404 Not Found: " + requestPath + "\n" + e.getMessage());
        }
    }

    /**
     * Helper mínimo para no “importar” el core aquí con generics raros.
     * Si tu RouteProvider.Result es record o class, esto lo normaliza.
     */
    private record RouteProviderResult(HtmlComponent component) {
        static RouteProviderResult from(com.ciro.jreactive.router.RouteProvider.Result r) {
            return new RouteProviderResult((HtmlComponent) r.component());
        }
    }
}
