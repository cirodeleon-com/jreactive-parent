/* === File: jreactive-standalone/src/main/java/com/ciro/jreactive/standalone/JReactiveServer.java === */
package com.ciro.jreactive.standalone;

import com.ciro.jreactive.CallGuard;
import com.ciro.jreactive.PageResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

import static io.undertow.Handlers.websocket;

public class JReactiveServer {

    private final int port;

    private final SimpleRouteRegistry registry;
    private final PageResolver pageResolver;

    private final ObjectMapper mapper;
    private final CallGuard callGuard;

    private final ScheduledExecutorService scheduler;
    private final StandaloneSessionManager sessionManager;

    public JReactiveServer(int port) {
        this.port = port;

        this.registry = new SimpleRouteRegistry();
        this.pageResolver = new PageResolver(registry);

        this.mapper = ObjectMapperFactory.create();

        Validator validator;
        try {
            validator = Validation.buildDefaultValidatorFactory().getValidator();
        } catch (Exception e) {
            validator = null;
            System.err.println("⚠️ ADVERTENCIA: No se encontró proveedor de validación.");
        }

        this.callGuard = new CallGuard(validator, mapper);
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.sessionManager = new StandaloneSessionManager();
    }

    public void addRoute(String path, Supplier<com.ciro.jreactive.HtmlComponent> factory) {
        registry.add(path, factory);
    }

    public void start() {

        ClassLoader cl = Main.class.getClassLoader();

        // ✅ /js/* -> classpath:/static/js/*
        ResourceHandler jsHandler = new ResourceHandler(
                new ClassPathResourceManager(cl, "static/js")
        );
        jsHandler.setCacheTime(0);

        // Opcional: /static/* -> classpath:/static/*
        ResourceHandler staticHandler = new ResourceHandler(
                new ClassPathResourceManager(cl, "static")
        );
        staticHandler.setCacheTime(0);

        // Endpoints
        WsEndpoint wsEndpoint = new WsEndpoint(pageResolver, mapper, scheduler, sessionManager);
        PageEndpoint pageEndpoint = new PageEndpoint(registry, sessionManager);
        CallEndpoint callEndpoint = new CallEndpoint(pageResolver, callGuard, mapper, sessionManager);

        // ✅ Fallback REAL: si no matchea ningún prefix, cae aquí (esto SÍ existe en Undertow 2.3.x)
        HttpHandler fallback = exchange -> {
            sessionManager.ensureSession(exchange);
            sessionManager.touchNoCache(exchange);
            pageEndpoint.handleRequest(exchange);
        };

        // ✅ PathHandler con fallback por constructor
        PathHandler routes = new PathHandler(fallback);

        routes.addPrefixPath("/ws", websocket(wsEndpoint::onConnect));
        routes.addPrefixPath("/call", callEndpoint);
        routes.addPrefixPath("/js", jsHandler);
        routes.addPrefixPath("/static", staticHandler);

        Undertow server = Undertow.builder()
                .addHttpListener(port, "0.0.0.0")
                .setHandler(routes)
                .build();

        server.start();
        System.out.println("🚀 JReactive Standalone corriendo en http://localhost:" + port);
        System.out.println("📦 Runtime JS: http://localhost:" + port + "/js/jreactive-runtime.js");
    }
}
