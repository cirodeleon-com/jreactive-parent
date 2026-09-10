package com.ciro.jreactive;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Decide si una escritura de estado que llega desde el navegador puede entrar.
 *
 * <p>La ruta profunda ya conforma el valor entrante al tipo del campo destino.
 * La ruta raíz no: asigna el valor crudo del JSON. Un valor con la forma
 * equivocada no solo queda mal en la variable reactiva, sino que se difunde a
 * los demás clientes, se publica al topic compartido y se persiste.
 *
 * <p>Punto único de política de la frontera cliente to servidor: aquí entran
 * las reglas siguientes (autorización de mutación, claves permitidas, tamaño
 * del payload) sin volver a intervenir el protocolo.
 */
public final class StateWriteGuard {

    /** Veredicto de una escritura entrante: aceptada con el valor ya conformado, o rechazada. */
    public record Verdict(boolean accepted, Object value, String reason) {

        static Verdict accept(Object value) {
            return new Verdict(true, value, null);
        }

        static Verdict reject(String reason) {
            return new Verdict(false, null, reason);
        }
    }

    private final ObjectMapper mapper;

    public StateWriteGuard(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Decide si el valor entrante puede escribirse sobre el binding destino.
     *
     * @return veredicto aceptado, con el valor ya convertido al tipo esperado, o rechazado.
     */
    public Verdict inspect(ReactiveVar<?> target, Object incoming) {
        java.lang.reflect.Type expected = expectedType(target);
        if (expected == null || incoming == null) {
            return Verdict.accept(incoming);
        }
        return conform(expected, incoming);
    }

    private Verdict conform(java.lang.reflect.Type expected, Object incoming) {
        try {
            return Verdict.accept(mapper.convertValue(incoming, mapper.constructType(expected)));
        } catch (IllegalArgumentException e) {
            return Verdict.reject("forma incompatible con el tipo declarado");
        }
    }

    /**
     * Tipo esperado del binding. El tipo declarado es transitorio y se pierde al
     * restaurar la sesión, así que en ese caso se usa el tipo del valor vigente,
     * que sí sobrevive a la serialización.
     */
    private java.lang.reflect.Type expectedType(ReactiveVar<?> target) {
        java.lang.reflect.Type declared = target.getGenericType();
        if (declared != null) {
            return declared;
        }
        Object current = target.get();
        return (current != null) ? current.getClass() : null;
    }
}