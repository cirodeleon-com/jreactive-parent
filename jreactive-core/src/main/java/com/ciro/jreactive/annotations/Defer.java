package com.ciro.jreactive.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ejecuta el método en un Hilo Virtual después del renderizado inicial.
 * El resultado se inyectará automáticamente en la variable @State indicada.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Defer {
    /** El nombre de la variable @State donde se guardará el resultado */
    String value();
    String fallback() default "";

    /**
     * Timeout en milisegundos para la tarea diferida.
     * Si expira, el resultado se descarta y el fallback permanece visible.
     * 0 = sin timeout (espera indefinida, no recomendado en producción).
     */
        /**
         * Timeout en milisegundos para la tarea diferida.
         * Si expira, el resultado se descarta y el fallback permanece visible.
         * 0 = sin timeout (espera indefinida, no recomendado en producción).
         */
        long timeout() default 30000;

        /**
         * HTML de error que se renderiza en el DOM (por el motor SSR) si la tarea falla o supera el timeout.
         * El HTML se pre-renderiza en un div oculto; al ocurrir el error, el runtime JS sólo lo hace visible
         * (sin transportar HTML por WebSocket, eliminando riesgo de XSS).
         * Si se deja vacío, el error sólo se loguea en stderr (comportamiento histórico).
         */
        String errorFallback() default "";
    }/* === File: jreactive-core/src/main/java/com/ciro/jreactive/Bind.java === === */