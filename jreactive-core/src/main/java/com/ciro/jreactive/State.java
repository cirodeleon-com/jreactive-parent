package com.ciro.jreactive;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.lang.annotation.ElementType.FIELD;

/**
 * Marca un campo como "raíz de estado":
 *  - Puede ser un POJO normal (User, Address, etc.)
 *  - Puede ser una lista (List<Order>, etc.)
 *  - Se expone a las plantillas como {{user.*}}
 */
@Retention(RUNTIME)
@Target(FIELD)
public @interface State {
    /** Nombre lógico; si está vacío, usa el nombre del campo */
    String value() default "";
}

