package com.ciro.jreactive.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declara los roles requeridos para ejecutar un método {@code @Call}.
 * <p>
 * Cuando el runtime de JReactive procese esta anotación (vía interceptor en
 * {@code PageController.callMethod()}), denegará el acceso si el
 * {@code Principal} transportado en el hilo virtual no posee ninguno de los
 * roles listados.
 * <p>
 * Hasta que ese interceptor exista, la verificación debe hacerse
 * programáticamente dentro del método como defensa en profundidad.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Authorize {
    String[] roles() default {};
}