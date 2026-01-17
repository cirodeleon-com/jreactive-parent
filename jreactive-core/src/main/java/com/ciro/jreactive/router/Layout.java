package com.ciro.jreactive.router;

import com.ciro.jreactive.HtmlComponent;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Inherited;

import static java.lang.annotation.ElementType.TYPE;

@Retention(RUNTIME)
@Target(TYPE)
@Inherited
public @interface Layout {
    // La clase del componente que actuará como layout
    Class<? extends HtmlComponent> value();
}