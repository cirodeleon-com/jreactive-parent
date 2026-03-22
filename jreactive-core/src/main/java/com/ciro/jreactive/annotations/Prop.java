package com.ciro.jreactive.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.lang.annotation.ElementType.FIELD;

/**
 * Marca un campo para inyección de datos Unidireccional (One-Way Binding) desde el padre.
 * A diferencia de @Bind, no envuelve el valor en un ReactiveVar, permitiendo el uso de tipos primitivos limpios.
 */
@Retention(RUNTIME)
@Target(FIELD)
public @interface Prop {
    /** Clave a exponer; si se deja vacío usa el nombre del campo */
    String value() default "";
}