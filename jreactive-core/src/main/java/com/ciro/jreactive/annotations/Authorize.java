package com.ciro.jreactive.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;

/**
 * Marca un metodo @Call (o una clase de componente completa) como protegido.
 *
 * La decision real la toma el AuthorizationProvider que registre la app host
 * (AuthorizationProvider.Registry.setProvider(...)).
 *
 * Semantica:
 *  - roles() vacio  = "cualquier principal autenticado" (el provider decide como comparar).
 *  - Fail-closed    = si un metodo lleva @Authorize y NO hay provider registrado, se NIEGA.
 *  - Los metodos SIN esta anotacion no pasan por autorizacion (comportamiento historico).
 */
@Target({METHOD, TYPE})
@Retention(RUNTIME)
public @interface Authorize {

    /** Roles / authorities requeridos. El provider define la comparacion exacta. */
    String[] roles() default {};

    /** Metadato libre opcional (p. ej. una expresion) para providers avanzados. */
    String value() default "";
}