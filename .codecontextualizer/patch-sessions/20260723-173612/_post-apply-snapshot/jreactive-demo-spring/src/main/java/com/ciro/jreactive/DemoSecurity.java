package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Authorize;
import com.ciro.jreactive.spi.AuthorizationProvider;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.Principal;
import java.util.Collections;
import java.util.Set;

/**
 * Infraestructura mínima de autenticación para el demo de @Authorize.
 * Consolidada en un solo archivo para reducir superficie.
 */
public final class DemoSecurity {

    private DemoSecurity() {}

    // ──────────────────────────────────────────────
    // 1. Principal ficticio (guardable en HttpSession)
    // ──────────────────────────────────────────────

    public static final class DemoPrincipal implements Principal {
        private final String name;
        private final Set<String> roles;

        public DemoPrincipal(String name, Set<String> roles) {
            this.name = name;
            this.roles = (roles == null) ? Set.of() : Collections.unmodifiableSet(Set.copyOf(roles));
        }

        @Override
        public String getName() { return name; }

        public Set<String> getRoles() { return roles; }
    }

    // ──────────────────────────────────────────────
    // 2. Filter que envuelve la request para exponer el Principal
    // ──────────────────────────────────────────────

    public static final String SESSION_KEY = "jrx.demo.principal";

    @Component
    public static class DemoAuthFilter implements Filter {

        @Override
        public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
                throws IOException, ServletException {

            HttpServletRequest request = (HttpServletRequest) req;
            DemoPrincipal principal = null;

            HttpSession session = request.getSession(false);
            if (session != null) {
                Object raw = session.getAttribute(SESSION_KEY);
                if (raw instanceof DemoPrincipal dp) {
                    principal = dp;
                }
            }

            if (principal != null) {
                chain.doFilter(new AuthenticatedRequest(request, principal), res);
            } else {
                chain.doFilter(req, res);
            }
        }

        private static final class AuthenticatedRequest extends HttpServletRequestWrapper {
            private final DemoPrincipal principal;

            AuthenticatedRequest(HttpServletRequest request, DemoPrincipal principal) {
                super(request);
                this.principal = principal;
            }

            @Override
            public Principal getUserPrincipal() {
                return principal;
            }
        }
    }

    // ──────────────────────────────────────────────
    // 3. AuthorizationProvider concreto (fail-closed)
    // ──────────────────────────────────────────────

    public static class DemoAuthorizationProvider implements AuthorizationProvider {

        @Override
        public boolean isAuthorized(AuthContext ctx, HtmlComponent component,
                                     java.lang.reflect.Method method, Authorize meta) {
            if (!ctx.isAuthenticated()) {
                return false;
            }

            Set<String> userRoles;
            if (ctx.principal() instanceof DemoPrincipal dp) {
                userRoles = dp.getRoles();
            } else {
                userRoles = ctx.roles();
            }

            String[] required = meta.roles();
            if (required == null || required.length == 0) {
                return true; // @Authorize sin roles = cualquier autenticado
            }

            for (String role : required) {
                if (userRoles.contains(role)) {
                    return true;
                }
            }
            return false;
        }
    }

    // ──────────────────────────────────────────────
    // 4. @Configuration: registra provider + filtro
    // ──────────────────────────────────────────────

    @Configuration
    public static class DemoAuthConfig {

        private final DemoAuthFilter demoAuthFilter;

        public DemoAuthConfig(DemoAuthFilter demoAuthFilter) {
            this.demoAuthFilter = demoAuthFilter;
        }

        @PostConstruct
        public void registerAuthorizationProvider() {
            AuthorizationProvider.Registry.setProvider(new DemoAuthorizationProvider());
            System.out.println("🔐 [Demo] DemoAuthorizationProvider registrado en AuthorizationProvider.Registry");
        }

        @Bean
        public FilterRegistrationBean<DemoAuthFilter> demoAuthFilterRegistration() {
            FilterRegistrationBean<DemoAuthFilter> registration = new FilterRegistrationBean<>(demoAuthFilter);
            registration.addUrlPatterns("/*");
            registration.setOrder(0);
            registration.setName("demoAuthFilter");
            return registration;
        }
    }
}