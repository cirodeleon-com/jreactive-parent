package com.ciro.jreactive;

import com.ciro.jreactive.router.RouteProvider;
import com.ciro.jreactive.store.CaffeineStateStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.socket.CloseStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

class JrxSockJsHandshakeEventIntegrationTest {

    @Test
    void handshakeContextReachesEventPushPipeline()
            throws Exception {

        AstComponentEngine.installAsDefault();

        JrxSpringEventPushIntegrationTest.EventPage page =
                new JrxSpringEventPushIntegrationTest.EventPage();

        RouteProvider routes = path -> {
            org.junit.jupiter.api.Assertions.assertEquals(
                    "/bandeja",
                    path,
                    "La ruta extraída por PathInterceptor no llegó al PageResolver."
            );

            return new RouteProvider.Result(
                    page,
                    Map.of()
            );
        };

        PageResolver resolver =
                new PageResolver(
                        routes,
                        new CaffeineStateStore()
                );

        ObjectMapper mapper =
                new ObjectMapper();

        LocalMessageBroker broker =
                new LocalMessageBroker();

        JrxHubManager manager =
                new JrxHubManager(
                        resolver,
                        mapper,
                        broker
                );

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.setParameter(
                "path",
                "/bandeja"
        );

        request.setParameter(
                "since",
                "0"
        );

        String expectedSessionId =
                request.getSession(true).getId();

        Map<String, Object> handshakeAttributes =
                new HashMap<>();

        boolean accepted =
                new PathInterceptor().beforeHandshake(
                        new ServletServerHttpRequest(request),
                        null,
                        null,
                        handshakeAttributes
                );

        org.junit.jupiter.api.Assertions.assertTrue(
                accepted,
                "PathInterceptor rechazó el handshake."
        );

        org.junit.jupiter.api.Assertions.assertEquals(
                "/bandeja",
                handshakeAttributes.get("path"),
                "El handshake no conservó la ruta solicitada."
        );

        org.junit.jupiter.api.Assertions.assertEquals(
                expectedSessionId,
                handshakeAttributes.get("sessionId"),
                "El handshake no conservó la sesión HTTP."
        );

        org.junit.jupiter.api.Assertions.assertEquals(
                "0",
                handshakeAttributes.get("since"),
                "El handshake no conservó el cursor de recuperación."
        );

        WsConfig config =
                new WsConfig();

        config.setEnabledBackpressure(false);

        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();

        DelegatingWebSocketHandler handler =
                new DelegatingWebSocketHandler(
                        resolver,
                        mapper,
                        scheduler,
                        config,
                        manager
                );

        JrxSpringEventPushIntegrationTest.CapturingSpringSession session =
                new JrxSpringEventPushIntegrationTest.CapturingSpringSession(
                        "session-incorrecta",
                        "/ruta-incorrecta"
                );

        session.getAttributes().clear();
        session.getAttributes().putAll(
                handshakeAttributes
        );

        handler.afterConnectionEstablished(
                session
        );

        try {
            manager.publish(
                    "demo.spring.changed"
            );

            org.junit.jupiter.api.Assertions.assertTrue(
                    page.awaitHandled(),
                    "La página conectada mediante el handshake no recibió @OnEvent."
            );

            org.junit.jupiter.api.Assertions.assertEquals(
                    1,
                    page.count,
                    "El evento no dejó el estado esperado."
            );

            org.junit.jupiter.api.Assertions.assertTrue(
                    session.awaitStatePush(),
                    "El estado generado por @OnEvent no llegó a la sesión creada desde el handshake."
            );

            org.junit.jupiter.api.Assertions.assertNotNull(
                    session.statePayload(),
                    "La sesión Spring no recibió el payload reactivo."
            );
        } finally {
            handler.afterConnectionClosed(
                    session,
                    new CloseStatus(
                            1002,
                            "test"
                    )
            );

            manager.evict(
                    expectedSessionId,
                    "/bandeja"
            );

            scheduler.shutdownNow();
        }
    }
}