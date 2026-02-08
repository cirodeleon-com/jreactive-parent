package com.ciro.jreactive.annotations;

import java.lang.annotation.*;

/**
 * Marca un componente para que viva EXCLUSIVAMENTE en la memoria RAM (L1).
 * NUNCA se serializará ni se enviará a Redis/Base de Datos.
 * Ideal para combinar con @Client.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Stateless {
}