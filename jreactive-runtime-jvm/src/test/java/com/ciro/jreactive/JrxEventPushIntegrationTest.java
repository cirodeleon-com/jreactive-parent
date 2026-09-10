package com.ciro.jreactive;

import com.ciro.jreactive.annotations.OnEvent;
import com.ciro.jreactive.annotations.StatefulRam;
import com.ciro.jreactive.spi.JrxSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class JrxEventPushIntegrationTest {

    @StatefulRam
    static final class EventPage extends HtmlComponent {

        @State
        public int count;

        private final CountDownLatch handled =
                new CountDownLatch(1);

        @OnEvent("demo.changed")
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

    static final class CapturingSession implements JrxSession {

        private final Map<String, Object> attributes =
                new ConcurrentHashMap<>();

        private final CountDownLatch pushed =
                new CountDownLatch(1);

        private volatile boolean open = true;

        @Override
        public String getId() {
            return "event-push-session";
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void sendText(String json) {
            if (json.indexOf("\"k\":\"count\"") >= 0
                    && json.indexOf("\"v\":1") >= 0) {
                pushed.countDown();
            }
        }

        @Override
        public void close() {
            open = false;
        }

        @Override
        public void setAttr(String key, Object value) {
            attributes.put(key, value);
        }

        @Override
        public Object getAttr(String key) {
            return attributes.get(key);
        }

        boolean awaitPush() throws InterruptedException {
            return pushed.await(2, TimeUnit.SECONDS);
        }
    }

    @BeforeAll
    static void installEngine() {
        AstComponentEngine.installAsDefault();
    }

    @Test
    void publishedEventReachesConnectedProtocolSession()
            throws InterruptedException {

        ObjectMapper mapper = new ObjectMapper();
        LocalMessageBroker broker = new LocalMessageBroker();

        JrxHubManager manager =
                new JrxHubManager(null, mapper, broker);

        EventPage page = new EventPage();
        page._initIfNeeded();
        page._mountRecursive();

        CapturingSession session =
                new CapturingSession();

        JrxProtocolHandler protocol =
                new JrxProtocolHandler(
                        page,
                        mapper,
                        null,
                        false,
                        64,
                        1,
                        null,
                        null
                );

        protocol.onOpen(session, null, 0);

        try {
            manager.publish("demo.changed");

            org.junit.jupiter.api.Assertions.assertTrue(
                    page.awaitHandled(),
                    "El evento publicado no llegó al método @OnEvent."
            );

            org.junit.jupiter.api.Assertions.assertEquals(
                    1,
                    page.count,
                    "El manejador @OnEvent no dejó el estado esperado."
            );

            org.junit.jupiter.api.Assertions.assertTrue(
                    session.awaitPush(),
                    "El @OnEvent cambió el estado, pero JrxProtocolHandler no lo entregó a la sesión."
            );
        } finally {
            protocol.onClose(session);
        }
    }
}