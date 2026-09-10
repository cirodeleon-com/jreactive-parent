package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Defer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DeferMountAndReloadTest {

    @BeforeAll
    static void setup() {
        AstComponentEngine.installAsDefault();
    }

    static final class MountPage extends HtmlComponent {

        @State
        public String data;

        final AtomicInteger calls = new AtomicInteger();
        final CountDownLatch applied = new CountDownLatch(1);

        @Defer("data")
        public String load() {
            calls.incrementAndGet();
            return "ready";
        }

        @Override
        protected String template() {
            return "<div>{{data}}</div>";
        }
    }

    static final class ReloadPage extends HtmlComponent {

        @State
        public String data;

        final AtomicInteger calls = new AtomicInteger();
        final CountDownLatch firstStarted = new CountDownLatch(1);
        final CountDownLatch releaseFirst = new CountDownLatch(1);
        final CountDownLatch secondApplied = new CountDownLatch(1);

        @Defer("data")
        public String load() throws InterruptedException {
            int invocation = calls.incrementAndGet();

            if (invocation == 1) {
                firstStarted.countDown();
                releaseFirst.await(2, TimeUnit.SECONDS);
            }

            return "run-" + invocation;
        }

        @Override
        protected String template() {
            return "<div>{{data}}</div>";
        }
    }

    @Test
    void deferredLoadWaitsUntilMount() throws Exception {
        MountPage page = new MountPage();
        page._initIfNeeded();

        @SuppressWarnings("unchecked")
        ReactiveVar<Object> data =
                (ReactiveVar<Object>) page.getRawBindings().get("data");

        data.onChange(value -> {
            if ("ready".equals(value)) {
                page.applied.countDown();
            }
        });

        assertThat(page.calls.get()).isZero();

        page._mountRecursive();

        assertThat(page.applied.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(page.calls.get()).isEqualTo(1);
        assertThat(page.data).isEqualTo("ready");
    }

    @Test
    void reloadsWhileRunningAreCoalesced() throws Exception {
        ReloadPage page = new ReloadPage();
        page._initIfNeeded();

        @SuppressWarnings("unchecked")
        ReactiveVar<Object> data =
                (ReactiveVar<Object>) page.getRawBindings().get("data");

        data.onChange(value -> {
            if ("run-2".equals(value)) {
                page.secondApplied.countDown();
            }
        });

        page._mountRecursive();

        assertThat(page.firstStarted.await(1, TimeUnit.SECONDS)).isTrue();

        page.reloadDeferred("data");
        page.reloadDeferred("data");
        page.reloadDeferred("data");

        page.releaseFirst.countDown();

        assertThat(page.secondApplied.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(page.calls.get()).isEqualTo(2);
        assertThat(page.data).isEqualTo("run-2");
    }
}