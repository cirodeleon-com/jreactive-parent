package com.ciro.jreactive;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Respuesta HTTP de una página: el código de estado y las cabeceras que la página decide en
 * {@link HttpPage#onRequest(JrxResponse)}. Empieza en 200 y sin cabeceras propias.
 */
public final class JrxResponse {

    private int status = 200;
    private final Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    /** Fija el código de estado, entre 100 y 599. */
    public JrxResponse status(int status) {
        this.status = requireStatus(status);
        return this;
    }

    /** Fija una cabecera; si ya había una con ese nombre, sin distinguir mayúsculas, la reemplaza. */
    public JrxResponse header(String name, String value) {
        headers.put(requireName(name), requireValue(value));
        return this;
    }

    public int status() {
        return status;
    }

    /** Cabeceras que fijó la página, de solo lectura. */
    public Map<String, String> headers() {
        return Collections.unmodifiableMap(headers);
    }

    static int requireStatus(int status) {
        if (status < 100 || status > 599) {
            throw new IllegalArgumentException("Código de estado HTTP fuera de rango: " + status);
        }
        return status;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank() || hasLineBreak(name)) {
            throw new IllegalArgumentException("Nombre de cabecera HTTP inválido");
        }
        return name;
    }

    private static String requireValue(String value) {
        if (value == null || hasLineBreak(value)) {
            throw new IllegalArgumentException("Valor de cabecera HTTP inválido");
        }
        return value;
    }

    /** Un salto de línea en una cabecera permitiría inyectar otras cabeceras en la respuesta. */
    private static boolean hasLineBreak(String text) {
        return text.indexOf('\r') >= 0 || text.indexOf('\n') >= 0;
    }
}