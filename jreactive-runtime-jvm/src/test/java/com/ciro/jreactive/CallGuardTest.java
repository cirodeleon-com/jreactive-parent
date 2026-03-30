package com.ciro.jreactive;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CallGuard - Pruebas de Seguridad y Rate Limiting")
class CallGuardTest {

    private CallGuard guard;
    private ObjectMapper mapper;
    private Validator validator;

    // --- Clases Dummy para probar la validación ---
    static class FormularioFalso {
        @NotBlank(message = "El nombre no puede estar vacío")
        public String nombre;
    }

    static class ControladorFalso {
        public void guardarRegistro(@jakarta.validation.Valid FormularioFalso form) {}
    }

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        try {
            // Inicializa el validador real que usa Spring Boot por debajo
            validator = Validation.buildDefaultValidatorFactory().getValidator();
        } catch (Exception e) {
            validator = null;
        }
        guard = new CallGuard(validator, mapper); 
    }

    @Test
    @DisplayName("Debe limitar las peticiones a ~60 por segundo")
    void testRateLimiting() {
        String sessionKey = "sesion-hacker:Login.submit";
        int peticionesExitosas = 0;
        for (int i = 0; i < 100; i++) {
            if (guard.tryConsume(sessionKey)) peticionesExitosas++;
        }
        assertThat(peticionesExitosas).isBetween(60, 65);
    }

    @Test
    @DisplayName("Debe aislar los límites de tasa entre usuarios")
    void testRateLimitingIsolation() {
        String user1 = "sesion-1:Click";
        String user2 = "sesion-2:Click";
        for (int i = 0; i < 100; i++) guard.tryConsume(user1);
        
        assertThat(guard.tryConsume(user1)).isFalse();
        assertThat(guard.tryConsume(user2)).isTrue();
    }

    @Test
    @DisplayName("Debe formatear correctamente un JSON de error estándar")
    void testErrorJsonFormatting() {
        String json = guard.errorJson("NOT_FOUND", "El método no existe");
        assertThat(json).contains("\"ok\":false").contains("\"code\":\"NOT_FOUND\"");
    }

    @Test
    @DisplayName("Debe atrapar violaciones de Jakarta Validation y escupir el JSON correcto")
    void testValidation() throws Exception {
        if (validator == null) return; // Si no hay validador en el classpath, ignorar

        ControladorFalso controller = new ControladorFalso();
        Method metodo = ControladorFalso.class.getMethod("guardarRegistro", FormularioFalso.class);

        FormularioFalso formInvalido = new FormularioFalso(); // nombre = null, fallará el @NotBlank
        
        var violaciones = guard.validateParams(controller, metodo, new Object[]{formInvalido});
        
        assertThat(violaciones).isNotEmpty();

        // Verificamos que el conversor a JSON haga su trabajo
        String jsonError = guard.validationJson(violaciones);
        
        assertThat(jsonError).contains("\"ok\":false");
        assertThat(jsonError).contains("\"code\":\"VALIDATION\"");
        assertThat(jsonError).contains("El nombre no puede estar vacío");
    }
}