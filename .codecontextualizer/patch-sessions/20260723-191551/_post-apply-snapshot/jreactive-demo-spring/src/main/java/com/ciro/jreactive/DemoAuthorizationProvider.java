package com.ciro.jreactive;

import com.ciro.jreactive.spi.AuthorizationProvider;

import java.lang.reflect.Method;

/**
 * Provider de autorización para el demo.
 *
 * <p>Simula autenticación leyendo el flag {@code adminMode} directamente del componente
 * {@link SecurityDemoPage}. En producción, reemplaza esto por un provider que consulte
 * tu sistema de seguridad real (Spring Security, JWT, etc.) usando el
 * {@link AuthContext} que el framework captura del hilo HTTP.
 *
 * <p>Regístralo una sola vez al arrancar:
 * <pre>
 *   AuthorizationProvider.Registry.setProvider(new DemoAuthorizationProvider());
 * </pre>
 */
public class DemoAuthorizationProvider implements AuthorizationProvider {

    @Override
    public boolean isAuthorized(AuthContext ctx, HtmlComponent component, Method method, Authorize meta) {
        // Si el método no declara roles, se permite (comportamiento histórico: opt-in).
        if (meta.roles().length == 0) {
            return true;
        }

        // --- Demo: SecurityDemoPage simula el rol ADMIN con su flag interno ---
        if (component instanceof SecurityDemoPage demoPage) {
            return demoPage.adminMode;
        }

        // --- Producción (fallback): usar los roles reales del AuthContext ---
        for (String role : meta.roles()) {
            if (ctx.roles().contains(role)) {
                return true;
            }
        }
        return false;
    }
}