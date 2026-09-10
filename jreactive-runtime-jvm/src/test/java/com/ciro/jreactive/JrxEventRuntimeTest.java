package com.ciro.jreactive;

import com.ciro.jreactive.annotations.OnEvent;
import com.ciro.jreactive.annotations.StatefulRam;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JrxEventRuntimeTest {

    @StatefulRam
    static final class LivePage extends HtmlComponent {

        int calls;

        @OnEvent("demo.changed")
        public void refresh() {
            calls++;
        }

        @Override
        protected String template() {
            return "<span>live</span>";
        }
    }

    @Test
    void connectedPageReceivesEvent() {
        LivePage page =
                new LivePage();

        JrxEventRuntime.connectionOpened(page);

        JrxEventRuntime.publish(
                "demo.changed"
        );

        JrxEventRuntime.connectionClosed(page);

        assertEquals(1, page.calls);
    }

    @Test
    void closedPageStopsReceivingEvents() {
        LivePage page =
                new LivePage();

        JrxEventRuntime.connectionOpened(page);
        JrxEventRuntime.connectionClosed(page);

        JrxEventRuntime.publish(
                "demo.changed"
        );

        assertEquals(0, page.calls);
    }

    @Test
    void oneOfTwoConnectionsKeepsPageLive() {
        LivePage page =
                new LivePage();

        JrxEventRuntime.connectionOpened(page);
        JrxEventRuntime.connectionOpened(page);
        JrxEventRuntime.connectionClosed(page);

        JrxEventRuntime.publish(
                "demo.changed"
        );

        JrxEventRuntime.connectionClosed(page);

        assertEquals(1, page.calls);
    }
}