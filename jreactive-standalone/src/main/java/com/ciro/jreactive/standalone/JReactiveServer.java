package com.ciro.jreactive.standalone;

import com.ciro.jreactive.CallGuard;
import com.ciro.jreactive.HtmlComponent;
import com.ciro.jreactive.JrxProtocolHandler;
import com.ciro.jreactive.PageResolver;
// 👇 IMPORTS DE JACKSON (Vitales para que no se cierre la conexión)
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.util.Headers;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

import static io.undertow.Handlers.path;
import static io.undertow.Handlers.resource;
import static io.undertow.Handlers.websocket;

public class JReactiveServer {

    private final int port;
    private final SimpleRouteRegistry registry;
    
    // Dependencias del Core
    private final PageResolver pageResolver;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler;
    private final CallGuard callGuard;

    public JReactiveServer(int port) {
        this.port = port;
        this.registry = new SimpleRouteRegistry();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();

        // 1. 🔥 CONFIGURACIÓN DE JACKSON (Igual que en Spring)
        // Registramos los módulos para soportar Fechas, Optional y Records
        // Esto evita que el servidor cierre la conexión por errores de serialización
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())       
                .registerModule(new Jdk8Module())           
                .registerModule(new ParameterNamesModule()) 
                .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

        Validator validator;
        try {
            validator = Validation.buildDefaultValidatorFactory().getValidator();
        } catch (Exception e) {
            validator = null; 
            System.err.println("⚠️ ADVERTENCIA: No se encontró proveedor de validación.");
        }

        this.callGuard = new CallGuard(validator, objectMapper);
        this.pageResolver = new PageResolver(registry);
    }

    public void addRoute(String path, Supplier<HtmlComponent> factory) {
        registry.add(path, factory);
    }

    public void start() {
        // --- 1. Definir Callback del WebSocket ---
        WebSocketConnectionCallback wsCallback = (exchange, channel) -> {
            
            var session = new UndertowJrxSession(channel);
            String path = getQueryParam(exchange, "path");
            if (path == null) path = "/";

            System.out.println("🔌 Conexión WebSocket: " + session.getId() + " [" + path + "]");

            try {
                HtmlComponent page = pageResolver.getPage(session.getId(), path);

                var handler = new JrxProtocolHandler(
                    page, 
                    objectMapper, 
                    scheduler, 
                    true, 
                    100,  
                    50    
                );

                handler.onOpen(session);

                channel.getReceiveSetter().set(new AbstractReceiveListener() {
                    @Override
                    protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) {
                        handler.onMessage(session, message.getData());
                    }
                });

                channel.getCloseSetter().set(c -> {
                    handler.onClose(session);
                    System.out.println("❌ Desconexión: " + session.getId());
                });
                
                channel.resumeReceives();

            } catch (Exception e) {
                e.printStackTrace(); // Verás el error real si falla la conexión
                try { channel.close(); } catch (Exception ignored) {}
            }
        };

        // --- 2. Manejador de Recursos Estáticos ---
        // Busca en src/main/resources/static
        HttpHandler resourceHandler = resource(
            new ClassPathResourceManager(JReactiveServer.class.getClassLoader(), "static")
        );

        // --- 3. Manejador de Páginas HTML ---
        HttpHandler pageHandler = exchange -> {
            String requestPath = exchange.getRequestPath();
            
            try {
                var result = registry.resolve(requestPath);
                HtmlComponent component = result.component();
                String bodyHtml = component.render(); 
                
                // 🔥 HTML LIMPIO: Sin scripts manuales de conexión.
                // jreactive-runtime.js se auto-conecta al cargar (igual que en Spring).
                // Apuntamos a /js/jreactive-runtime.js
                String fullHtml = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <title>JReactive Standalone</title>
                        <script src="/js/jreactive-runtime.js"></script>
                        <style>body { font-family: sans-serif; }</style>
                    </head>
                    <body>
                        <div id="app">
                            %s
                        </div>
                    </body>
                    </html>
                """.formatted(bodyHtml);

                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html; charset=UTF-8");
                exchange.getResponseSender().send(fullHtml);

            } catch (Exception e) {
                // Si no es una página, delegamos a recursos estáticos (ej: cargar el .js)
                resourceHandler.handleRequest(exchange);
            }
        };

        // --- 4. Configurar Rutas ---
        HttpHandler pathHandler = path(pageHandler)
            .addPrefixPath("/ws", websocket(wsCallback));

        // --- 5. Arrancar ---
        Undertow server = Undertow.builder()
                .addHttpListener(port, "0.0.0.0")
                .setHandler(pathHandler)
                .build();

        server.start();
        System.out.println("🚀 JReactive Standalone corriendo en http://localhost:" + port);
    }

    private String getQueryParam(WebSocketHttpExchange exchange, String key) {
        Map<String, List<String>> params = exchange.getRequestParameters();
        if (params != null && params.containsKey(key)) {
            return params.get(key).get(0);
        }
        return null;
    }
}