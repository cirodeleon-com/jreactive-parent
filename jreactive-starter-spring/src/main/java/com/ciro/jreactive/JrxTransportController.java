package com.ciro.jreactive;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/jrx")
public class JrxTransportController {

    private final JrxHubManager hubs;
    private final PageResolver pageResolver;
    private final WsConfig wsConfig;
    private final JrxRequestQueue queue;

    public JrxTransportController(JrxHubManager hubs,
                                  PageResolver pageResolver,
                                  WsConfig wsConfig,
                                  JrxRequestQueue queue) {
        this.hubs = hubs;
        this.pageResolver = pageResolver;
        this.wsConfig = wsConfig;
        this.queue = queue;
    }

    private String sid(HttpServletRequest req) {
        return req.getSession(true).getId();
    }

    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sse(HttpServletRequest req,
                          @RequestParam(defaultValue = "/") String path,
                          @RequestParam(defaultValue = "0") long since) {

        String sessionId = sid(req);
        JrxPushHub hub = hubs.hub(sessionId, path);

        SseEmitter em = new SseEmitter(0L);
        AtomicBoolean open = new AtomicBoolean(true);

        JrxPushHub.JrxSink sink = new JrxPushHub.JrxSink() {
            @Override public boolean isOpen() { return open.get(); }

            @Override
            public void send(String json) throws IOException {
                em.send(SseEmitter.event().name("jrx").data(json));
            }

            @Override
            public void close() {
                open.set(false);
                try { em.complete(); } catch (Exception ignored) {}
            }
        };

        em.onCompletion(() -> {
            open.set(false);
            hub.unsubscribe(sink);
        });
        em.onTimeout(() -> {
            open.set(false);
            hub.unsubscribe(sink);
        });
        em.onError(_e -> {
            open.set(false);
            hub.unsubscribe(sink);
        });

        hub.subscribe(sink, since);
        return em;
    }

    @GetMapping(value = "/poll", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> poll(HttpServletRequest req,
                                    @RequestParam(defaultValue = "/") String path,
                                    @RequestParam(defaultValue = "0") long since) {
    	
    	String sid = sid(req);
    	
    	return queue.run(sid, path, () -> {
           var b = hubs.hub(sid(req), path).poll(since);
           return Map.of("seq", b.getSeq(), "batch", b.getBatch());
    	});
    }

    @PostMapping(value = "/set", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> set(HttpServletRequest req,
                                   @RequestParam(defaultValue = "/") String path,
                                   @RequestBody Map<String, Object> body) {

        String sessionId = sid(req);
        String k = (String) body.get("k");
        Object v = body.get("v");

        if (k == null) return Map.of("ok", false);

        // ✅ Serializar /set también (CRÍTICO) para que no compita con /call
        return queue.run(sessionId, path, () -> {
            hubs.hub(sessionId, path).set(k, v);

            if (wsConfig.isPersistentState()) {
                HtmlComponent page = pageResolver.getPage(sessionId, path);
                pageResolver.persist(sessionId, path, page);
            }

            return Map.of("ok", true);
        });
    }

    @PostMapping(value = "/leave", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void leave(HttpServletRequest req, @RequestBody Map<String, String> body) {
        String path = body.get("path");
        String sessionId = req.getSession(true).getId();

        System.out.println("📨 RECIBIDO /leave para Sesión: " + sessionId + " Path: " + path);
        System.out.println("   Configuración persistente: " + wsConfig.isPersistentState());

        if (!wsConfig.isPersistentState()) {
            pageResolver.evict(sessionId, path);
            hubs.evict(sessionId, path);
            System.out.println("👋 SSE/Poll: Memoria liberada para " + path);
        } else {
            System.out.println("💾 SSE/Poll: Memoria MANTENIDA (Persistent Mode ON)");
        }
    }
}
