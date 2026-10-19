package com.ciro.jreactive;

import com.ciro.jreactive.spi.AuthorizationProvider.AuthContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.security.Principal;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JrxAuthContextResolver - Identidad para @Authorize desde la petición")
class JrxAuthContextResolverTest {

    @Test
    @DisplayName("Sin usuario autenticado, la identidad es anónima")
    void sinUsuarioLaIdentidadEsAnonima() {
        AuthContext ctx = JrxAuthContextResolver.resolve(new MockHttpServletRequest());

        assertThat(ctx.isAuthenticated()).isFalse();
        assertThat(ctx.roles()).isEmpty();
    }

    @Test
    @DisplayName("Sin Spring Security, los roles salen del getRoles() del usuario")
    void tomaLosRolesDelUsuario() {
        RolesPrincipal ana = new RolesPrincipal("ana", List.of("ADMIN", "USER"));

        AuthContext ctx = JrxAuthContextResolver.resolve(requestWith(ana));

        assertThat(ctx.principalName()).isEqualTo("ana");
        assertThat(ctx.roles()).containsExactlyInAnyOrder("ADMIN", "USER");
        assertThat(ctx.principal()).isSameAs(ana);
    }

    @Test
    @DisplayName("Un usuario que no declara roles queda autenticado y sin roles")
    void usuarioSinRolesQuedaAutenticado() {
        Principal bruno = () -> "bruno";

        AuthContext ctx = JrxAuthContextResolver.resolve(requestWith(bruno));

        assertThat(ctx.isAuthenticated()).isTrue();
        assertThat(ctx.principalName()).isEqualTo("bruno");
        assertThat(ctx.roles()).isEmpty();
    }

    private static MockHttpServletRequest requestWith(Principal principal) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setUserPrincipal(principal);
        return request;
    }

    /** Usuario con roles propios, como el de la demo. */
    public static final class RolesPrincipal implements Principal {

        private final String name;
        private final Collection<String> roles;

        public RolesPrincipal(String name, Collection<String> roles) {
            this.name = name;
            this.roles = roles;
        }

        @Override
        public String getName() {
            return name;
        }

        public Collection<String> getRoles() {
            return roles;
        }
    }
}