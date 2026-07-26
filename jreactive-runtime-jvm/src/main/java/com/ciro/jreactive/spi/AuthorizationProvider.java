package com.ciro.jreactive.spi;

import com.ciro.jreactive.HtmlComponent;
import com.ciro.jreactive.annotations.Authorize;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * SPI de autorizacion para metodos @Call.
 *
 * La app host implementa esta interfaz y la registra UNA sola vez al arrancar:
 *
 *     AuthorizationProvider.Registry.setProvider((ctx, comp, method, meta) -> {
 *         if (!ctx.isAuthenticated()) return false;
 *         for (String r : meta.roles()) if (!ctx.roles().contains(r)) return false;
 *         return true;
 *     });
 *
 * Los metodos SIN @Authorize nunca llegan aqui (ruta identica a la historica).
 * Los metodos CON @Authorize se niegan si no hay provider registrado (fail-closed).
 */
public interface AuthorizationProvider {

    /**
     * @param ctx       identidad capturada en el hilo de request; nunca null (puede ser anonymous()).
     * @param component instancia del componente dueno del metodo (puede ser null en casos limite).
     * @param method    el metodo @Call solicitado.
     * @param meta      la anotacion @Authorize encontrada (en el metodo o en la clase).
     * @return true si se permite ejecutar el metodo.
     */
    boolean isAuthorized(AuthContext ctx, HtmlComponent component, Method method, Authorize meta);

    /** Identidad minima, desacoplada de cualquier framework de seguridad concreto. */
    record AuthContext(String principalName, Set<String> roles, Object principal) {

        public static AuthContext anonymous() {
            return new AuthContext(null, Set.of(), null);
        }

        public boolean isAuthenticated() {
            return principalName != null;
        }
    }

    /** Registro global del provider (un solo policy por app; mismo patron que AccessorRegistry). */
    final class Registry {
        private static volatile AuthorizationProvider provider;

        private Registry() {}

        public static void setProvider(AuthorizationProvider p) { provider = p; }

        public static AuthorizationProvider get() { return provider; }
    }

    /**
     * Puente hilo-de-request -> hilo-de-ejecucion.
     *
     * El adaptador de transporte captura el AuthContext en el hilo HTTP (donde existe el
     * contexto de seguridad) y lo re-inyecta DENTRO de la tarea, que corre en otro hilo
     * virtual (JrxRequestQueue). Sin este puente, la identidad se perderia.
     */
    final class Holder {
        private static final ThreadLocal<AuthContext> CTX = new ThreadLocal<>();

        private Holder() {}

        public static void set(AuthContext c) { CTX.set(c); }

        public static AuthContext get() {
            AuthContext c = CTX.get();
            return (c != null) ? c : AuthContext.anonymous();
        }

        public static void clear() { CTX.remove(); }
    }
}