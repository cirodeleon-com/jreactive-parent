package com.ciro.jreactive.spi;

import com.ciro.jreactive.HtmlComponent;

/**
 * Interfaz que reemplaza a la reflexión en tiempo de ejecución.
 * @param <T> El tipo concreto del componente (ej: UserPage)
 */
public interface ComponentAccessor<T extends HtmlComponent> {

    // Reemplaza a Field.set()
    void write(T component, String property, Object value);

    // Reemplaza a Field.get()
    Object read(T component, String property);

    // Reemplaza a Method.invoke()
    Object call(T component, String method, Object... args);
}