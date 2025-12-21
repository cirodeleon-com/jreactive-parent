package com.ciro.jreactive;

import java.util.Map;
import java.util.Objects;

final class ComponentEngine {

    record Rendered(String html, Map<String, ReactiveVar<?>> bindings) {}

    interface Strategy {
        Rendered render(HtmlComponent ctx);
    }

    // ✅ NO default acá (core no conoce Jsoup runtime)
    private static volatile Strategy strategy;

    static Rendered render(HtmlComponent ctx) {
        Strategy s = strategy;
        if (s == null) {
            throw new IllegalStateException(
                "ComponentEngine strategy not set. " +
                "You must call ComponentEngine.setStrategy(new JsoupComponentEngine()) " +
                "from jreactive-runtime-jvm (or your bootstrap) before rendering."
            );
        }
        return s.render(ctx);
    }

    static void setStrategy(Strategy newStrategy) {
        strategy = Objects.requireNonNull(newStrategy, "strategy must not be null");
    }

    private ComponentEngine() {}
}
