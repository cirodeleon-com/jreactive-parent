package com.ciro.jreactive;

import com.ciro.jreactive.spi.AuthorizationProvider.AuthContext;
import jakarta.servlet.http.HttpServletRequest;

import java.security.Principal;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Identidad del usuario para {@code @Authorize}, tomada de una petición servlet.
 *
 * <p>Los roles salen de las authorities de Spring Security cuando está en el classpath y, si
 * no hay ninguna, de un {@code getRoles()} público del principal. Todo por reflexión, para que
 * el starter no dependa de Spring Security.
 */
final class JrxAuthContextResolver {

    private static final String SPRING_AUTHENTICATION = "org.springframework.security.core.Authentication";

    private JrxAuthContextResolver() {
    }

    static AuthContext resolve(HttpServletRequest request) {
        Principal principal = request.getUserPrincipal();
        if (principal == null) {
            return AuthContext.anonymous();
        }
        return new AuthContext(principal.getName(), roles(principal), principal);
    }

    private static Set<String> roles(Principal principal) {
        Set<String> roles = new HashSet<>();
        addSpringAuthorities(principal, roles);
        if (roles.isEmpty()) {
            addDeclaredRoles(principal, roles);
        }
        return roles;
    }

    private static void addSpringAuthorities(Principal principal, Set<String> roles) {
        try {
            Class<?> authentication = Class.forName(SPRING_AUTHENTICATION);
            if (authentication.isInstance(principal)) {
                addAuthorityNames(authentication.getMethod("getAuthorities").invoke(principal), roles);
            }
        } catch (ClassNotFoundException ignored) {
            // Spring Security no está en el classpath: se prueba el siguiente origen de roles.
        } catch (Exception ignored) {
            // Error inesperado leyendo las authorities: la petición sigue, sin esos roles.
        }
    }

    private static void addAuthorityNames(Object authorities, Set<String> roles) throws ReflectiveOperationException {
        if (!(authorities instanceof Collection<?> granted)) {
            return;
        }
        for (Object authority : granted) {
            Object name = authority.getClass().getMethod("getAuthority").invoke(authority);
            if (name != null) {
                roles.add(String.valueOf(name));
            }
        }
    }

    private static void addDeclaredRoles(Principal principal, Set<String> roles) {
        try {
            Object declared = principal.getClass().getMethod("getRoles").invoke(principal);
            if (declared instanceof Collection<?> collection) {
                collection.forEach(role -> roles.add(String.valueOf(role)));
            }
        } catch (Exception ignored) {
            // El principal no expone roles: AuthContext.roles() queda vacío.
        }
    }
}