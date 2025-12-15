package com.ciro.jreactive;

import java.util.Map;
import java.util.Objects;

/**
 * Fachada del motor de componentes.
 *
 * Usa internamente una Strategy (por defecto RegexComponentEngine).
 * Desde fuera sigues usando: ComponentEngine.render(this)
 */
final class ComponentEngine {

    /**
     * Resultado del render: HTML + bindings.
     */
    record Rendered(String html, Map<String, ReactiveVar<?>> bindings) {}

    /**
     * Estrategia pluggable de renderizado.
     */
    interface Strategy {
        Rendered render(HtmlComponent ctx);
    }

    /** Estrategia actual. Por defecto: regex. */
    private static Strategy strategy = new JsoupComponentEngine();//new RegexComponentEngine();

    /**
     * Punto de entrada público: delega en la estrategia actual.
     */
    static Rendered render(HtmlComponent ctx) {
        return strategy.render(ctx);
    }

    /**
     * Permite cambiar la estrategia (por ejemplo a JsoupComponentEngine)
     * si en el futuro quieres usar otro motor.
     */
    static void setStrategy(Strategy newStrategy) {
        strategy = Objects.requireNonNull(newStrategy, "strategy must not be null");
    }

    private ComponentEngine() {
        // util-class
    }
}
