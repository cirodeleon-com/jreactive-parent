package com.ciro.jreactive.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.RetentionPolicy.RUNTIME;   
import static java.lang.annotation.ElementType.METHOD;;

@Target(METHOD)
@Retention(RUNTIME)
public @interface Call {

}
