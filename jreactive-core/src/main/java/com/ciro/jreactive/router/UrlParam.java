package com.ciro.jreactive.router;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Vincula una variable de la URL (Query String) ej: ?search=xyz
 * a un campo del componente. Se recomienda usar junto con @State o @Bind.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface UrlParam {
    String value() default ""; // Nombre del parámetro en la URL
}