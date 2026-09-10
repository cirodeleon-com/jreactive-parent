package com.ciro.jreactive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

final class JrxEventRuntime {

    private static final class LiveRegistration {

        private final AtomicInteger connections =
                new AtomicInteger();

        private final ReentrantLock execution =
                new ReentrantLock();
    }

    private record LiveTarget(
            HtmlComponent page,
            LiveRegistration registration
    ) {
    }

    private static final Map<HtmlComponent, LiveRegistration> LIVE =
            Collections.synchronizedMap(
                    new WeakHashMap<>()
            );

    private JrxEventRuntime() {
    }

    static void connectionOpened(
            ViewNode root
    ) {
        HtmlComponent page =
                page(root);

        if (page == null) {
            return;
        }

        synchronized (LIVE) {
            LIVE.computeIfAbsent(
                    page,
                    ignored -> new LiveRegistration()
            ).connections.incrementAndGet();
        }
    }

    static void connectionClosed(
            ViewNode root
    ) {
        HtmlComponent page =
                page(root);

        if (page == null) {
            return;
        }

        synchronized (LIVE) {
            closeRegistration(page);
        }
    }

    private static void closeRegistration(
            HtmlComponent page
    ) {
        LiveRegistration registration =
                LIVE.get(page);

        if (registration == null) {
            return;
        }

        if (registration.connections.decrementAndGet() <= 0) {
            LIVE.remove(page);
        }
    }

    static void publish(
            String event
    ) {
        for (LiveTarget target : snapshot()) {
            dispatch(target, event);
        }
    }

    private static List<LiveTarget> snapshot() {
        synchronized (LIVE) {
            List<LiveTarget> result =
                    new ArrayList<>(LIVE.size());

            LIVE.forEach(
                    (page, registration) ->
                            result.add(
                                    new LiveTarget(
                                            page,
                                            registration
                                    )
                            )
            );

            return result;
        }
    }

    private static void dispatch(
            LiveTarget target,
            String event
    ) {
        LiveRegistration registration =
                target.registration();

        if (registration.connections.get() <= 0) {
            return;
        }

        registration.execution.lock();
        try {
            dispatchLocked(target, event);
        } finally {
            registration.execution.unlock();
        }
    }

    private static void dispatchLocked(
            LiveTarget target,
            String event
    ) {
        if (target.registration()
                .connections
                .get() <= 0) {
            return;
        }

        JrxEventDispatcher.dispatch(
                target.page(),
                event
        );
    }

    private static HtmlComponent page(
            ViewNode root
    ) {
        return root instanceof HtmlComponent component
                ? component
                : null;
    }
}