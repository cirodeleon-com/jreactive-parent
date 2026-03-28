package com.ciro.jreactive.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un campo para sincronización Multijugador en Tiempo Real (Pub/Sub).
 * El estado se guarda en un tópico global y se ignora en la sesión local.
 * Funciona con @Stateful y @StatefulRam, pero es INCOMPATIBLE con @Stateless.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Shared {
    /** El nombre del tópico o sala (Ej: "chat-global", "mesa-poker-5") */
    String value() default "";
}