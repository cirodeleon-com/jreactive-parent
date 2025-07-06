package com.ciro.jreactive.router;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.RetentionPolicy.RUNTIME;   //  👈 importa la constante
import static java.lang.annotation.ElementType.TYPE;

//src/main/java/com/ciro/jreactive/router/Route.java
@Target(TYPE)
@Retention(RUNTIME)
public @interface Route {
 String path();         // ej. "/about"  (empieza con /)
}
