package com.ciro.jreactive;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.RetentionPolicy.RUNTIME;   //  👈 importa la constante
import static java.lang.annotation.ElementType.FIELD;

@Retention(RUNTIME)
@Target(FIELD)
public @interface Bind {
    /** Clave a exponer; si se deja vacío usa el nombre del campo */
    String value() default "";
    /** Si true, ignora cambios cliente→servidor */
    boolean readOnly() default false;
}
