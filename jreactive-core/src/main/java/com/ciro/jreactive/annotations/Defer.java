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
}