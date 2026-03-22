package com.ciro.jreactive.router;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;

/** Marca un campo o parámetro que debe llenarse con un valor del path. */
@Retention(RUNTIME)
@Target({FIELD, PARAMETER})
public @interface UrlVariable {
    String value(); // nombre del placeholder en la ruta
}
