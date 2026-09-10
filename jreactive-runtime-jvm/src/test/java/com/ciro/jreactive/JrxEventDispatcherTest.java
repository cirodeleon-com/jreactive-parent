package com.ciro.jreactive;

import com.ciro.jreactive.annotations.OnEvent;
import com.ciro.jreactive.annotations.StatefulRam;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JrxEventDispatcherTest {

    @StatefulRam
    static final class LivePage extends HtmlComponent {

        @State
        public int count;

        @OnEvent("demo.changed")
        public void refresh() {
            count++;
        }

        @Override
        protected String template() {
            return "<span>{{count}}</span>";
        }
    }

    static final class StatelessPage extends HtmlComponent {

        int calls;

        @OnEvent("demo.changed")
        public void refresh() {
            calls++;
        }

        @Override
        protected String template() {
            return "<span>stateless</span>";
        }
    }

    @Test
    void matchingEventExecutesHandler() {
        LivePage page =
                new LivePage();

        boolean handled =
                JrxEventDispatcher.dispatch(
                        page,
                        "demo.changed"
                );

        assertTrue(handled);
        assertEquals(1, page.count);
    }

    @Test
    void differentEventDoesNothing() {
        LivePage page =
                new LivePage();

        boolean handled =
                JrxEventDispatcher.dispatch(
                        page,
                        "other.changed"
                );

        assertFalse(handled);
        assertEquals(0, page.count);
    }

    @Test
    void statelessPageDoesNotReceiveEvent() {
        StatelessPage page =
                new StatelessPage();

        boolean handled =
                JrxEventDispatcher.dispatch(
                        page,
                        "demo.changed"
                );

        assertFalse(handled);
        assertEquals(0, page.calls);
    }
}