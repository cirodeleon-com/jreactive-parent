package com.ciro.jreactive;

import com.ciro.jreactive.annotations.OnEvent;
import com.ciro.jreactive.annotations.StatefulRam;
import com.ciro.jreactive.router.RouteProvider;
import com.ciro.jreactive.store.CaffeineStateStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class JrxSpringEventPushIntegrationTest {

    @StatefulRam
    static final class EventPage extends HtmlComponent {

        @State
        public int count;

        private final CountDownLatch handled =
                new CountDownLatch(1);

        @OnEvent("demo.spring.changed")
        public void refresh() {
            count++;
            handled.countDown();
        }

        boolean awaitHandled() throws InterruptedException {
            return handled.await(2, TimeUnit.SECONDS);
        }

        @Override
        protected String template() {
            return "<span>{{count}}</span>";
        }
    }

    static final class CapturingSpringSession
            implements WebSocketSession {

        private final Map<String, Object> attributes =
                new ConcurrentHashMap<>();

        private final CountDownLatch statePushed =
                new CountDownLatch(1);

        private volatile boolean open = true;
        private volatile String statePayload;

        CapturingSpringSession(
                String sessionId,
                String path
        ) {
            attributes.put("sessionId", sessionId);
            attributes.put("path", path);
            attributes.put("since", "0");
        }

        boolean awaitStatePush()
                throws InterruptedException {
            return statePushed.await(
                    2,
                    TimeUnit.SECONDS
            );
        }

        String statePayload() {
            return statePayload;
        }

        @Override
        public String getId() {
            return "spring-event-session";
        }

        @Override
        public URI getUri() {
            return null;
        }

        @Override
        public HttpHeaders getHandshakeHeaders() {
            return HttpHeaders.EMPTY;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return attributes;
        }

        @Override
        public Principal getPrincipal() {
            return null;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return null;
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return null;
        }

        @Override
        public String getAcceptedProtocol() {
            return null;
        }

        @Override
        public void setTextMessageSizeLimit(int messageSizeLimit) {
        }

        @Override
        public int getTextMessageSizeLimit() {
            return 64 * 1024;
        }

        @Override
        public void setBinaryMessageSizeLimit(int messageSizeLimit) {
        }

        @Override
        public int getBinaryMessageSizeLimit() {
            return 64 * 1024;
        }

        @Override
        public List<WebSocketExtension> getExtensions() {
            return List.of();
        }

        @Override
        public void sendMessage(
                WebSocketMessage<?> message
        ) throws IOException {
            if (message instanceof TextMessage text) {
                String payload = text.getPayload();

                if (payload.contains("\"k\":\"count\"")
                        && payload.contains("\"v\":1")) {
                    statePayload = payload;
                    statePushed.countDown();
                }
            }
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }

        @Override
        public void close(CloseStatus status) {
            open = false;
        }
    }

    @BeforeAll
    static void installEngine() {
        AstComponentEngine.installAsDefault();
    }

    @Test
    void publishedEventCrossesSpringWebSocketBoundary()
            throws Exception {

        EventPage page = new EventPage();

        RouteProvider routes =
                path -> new RouteProvider.Result(
                        page,
                        Map.of()
                );

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

        CapturingSpringSession session =
                new CapturingSpringSession(
                        "sid-spring-event",
                        "/"
                );

        handler.afterConnectionEstablished(session);

        try {
            manager.publish(
                    "demo.spring.changed"
            );

            org.junit.jupiter.api.Assertions.assertTrue(
                    page.awaitHandled(),
                    "El evento no atravesó el broker hasta @OnEvent."
            );

            org.junit.jupiter.api.Assertions.assertEquals(
                    1,
                    page.count,
                    "El manejador no dejó el estado esperado."
            );

            org.junit.jupiter.api.Assertions.assertTrue(
                    session.awaitStatePush(),
                    "El nuevo estado no atravesó JReactiveSocketHandler hasta WebSocketSession."
            );

            org.junit.jupiter.api.Assertions.assertNotNull(
                    session.statePayload(),
                    "Spring no recibió el TextMessage esperado."
            );
        } finally {
            handler.afterConnectionClosed(
                    session,
                    new CloseStatus(1002, "test")
            );

            manager.evict(
                    "sid-spring-event",
                    "/"
            );

            scheduler.shutdownNow();
        }
    }
}