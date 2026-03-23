package com.ciro.jreactive.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Convierte automáticamente una clase en un Web Component nativo (Custom Element).
 * JReactive generará el HTML y los puentes de eventos en tiempo de compilación.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface WebComponent {
    String tag(); // Ej: "sl-input", "md-button"
    String[] props() default {}; 
    String[] events() default {}; 
    String[] slots() default {"default"}; // Soporte nativo para Named Slots
}