package com.ciro.jreactive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de regresión del método {@code @Call login(String role)} añadido a
 * {@link SecureDemoPage}.
 *
 * <p>Cubre los cuatro caminos del switch de rol y el estado inicial declarado
 * por la página, para que un refactor posterior (p. ej. añadir {@code @Authorize})
 * no rompa silenciosamente el contrato actual.
 *
 * <p>Es un test puramente unitario: instancia la página con
 * {@code new SecureDemoPage()} y nunca arranca el contexto de Spring ni el motor
 * AOT. Solo verifica mutaciones sobre campos {@code @State} públicos.
 */
@DisplayName("SecureDemoPage - Switch de rol vía @Call login")
class SecureDemoPageTest {

    private SecureDemoPage page;

    @BeforeEach
    void setUp() {
        page = new SecureDemoPage();
    }

    @Test
    @DisplayName("Estado inicial: rol GUEST y mensaje de bienvenida")
    void initialState() {
        assertThat(page.currentRole).isEqualTo("GUEST");
        assertThat(page.message).contains("Bienvenido");
    }

    @Test
    @DisplayName("login(ADMIN) cambia currentRole y registra el cambio en message")
    void loginAdminRole() {
        page.login("ADMIN");
        assertThat(page.currentRole).isEqualTo("ADMIN");
        assertThat(page.message).isEqualTo("Rol cambiado a ADMIN.");
    }

    @Test
    @DisplayName("login normaliza casing: 'user' se convierte en 'USER'")
    void loginNormalizesCase() {
        page.login("user");
        assertThat(page.currentRole).isEqualTo("USER");
        assertThat(page.message).isEqualTo("Rol cambiado a USER.");
    }

    @Test
    @DisplayName("login(GUEST) vuelve explícitamente al rol por defecto")
    void loginGuestRole() {
        page.login("ADMIN");
        page.login("GUEST");
        assertThat(page.currentRole).isEqualTo("GUEST");
        assertThat(page.message).isEqualTo("Rol cambiado a GUEST.");
    }

    @Test
    @DisplayName("login recorta espacios antes de normalizar ('  admin  ' -> 'ADMIN')")
    void loginTrimsRoleBeforeNormalizing() {
        page.login("  admin  ");
        assertThat(page.currentRole).isEqualTo("ADMIN");
        assertThat(page.message).isEqualTo("Rol cambiado a ADMIN.");
    }

    @Test
    @DisplayName("login(null) y login(blank) cierran sesión y resetean a GUEST")
    void loginNullOrEmptyResetsToGuest() {
        page.login("ADMIN");
        assertThat(page.currentRole).isEqualTo("ADMIN");

        page.login(null);
        assertThat(page.currentRole).isEqualTo("GUEST");
        assertThat(page.message).isEqualTo("Sesión cerrada. Rol: GUEST.");

        page.login("   ");
        assertThat(page.currentRole).isEqualTo("GUEST");
        assertThat(page.message).isEqualTo("Sesión cerrada. Rol: GUEST.");
    }

    @Test
    @DisplayName("login con rol desconocido cae a GUEST y reporta el valor inválido")
    void loginUnknownRoleFallsBackToGuest() {
        page.login("HACKER");
        assertThat(page.currentRole).isEqualTo("GUEST");
        assertThat(page.message).isEqualTo("Rol desconocido 'HACKER'. Resetado a GUEST.");
    }
}