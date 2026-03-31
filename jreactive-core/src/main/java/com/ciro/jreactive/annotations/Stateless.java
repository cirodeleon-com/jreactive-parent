package com.ciro.jreactive.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Activa el modo 100% Stateless (Cero Memoria en Servidor).
 * El estado viaja firmado criptográficamente hacia el cliente en el HTML,
 * y regresa en cada petición HTTP para ser hidratado al vuelo.
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Stateless {
}
