package com.ciro.jreactive;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("JReactivePageControllerAutoConfiguration - Endpoint de páginas registrado por el starter")
class JReactivePageControllerAutoConfigurationTest {

    private static final AutoConfigurations PAGE_CONTROLLER =
            AutoConfigurations.of(JReactivePageControllerAutoConfiguration.class);

    /** Aplicación web con los colaboradores que el endpoint recibe del núcleo de JReactive. */
    private final WebApplicationContextRunner web = new WebApplicationContextRunner()
            .withConfiguration(PAGE_CONTROLLER)
            .withBean(PageResolver.class, () -> mock(PageResolver.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(CallGuard.class, () -> new CallGuard(mock(Validator.class), new ObjectMapper()))
            .withBean(WsConfig.class, WsConfig::new)
            .withBean(JrxRequestQueue.class, JrxRequestQueue::new)
            .withBean(JrxHubManager.class, () -> mock(JrxHubManager.class));

    @Test
    @DisplayName("Una aplicación web recibe el endpoint de páginas sin declararlo")
    void registraElEndpointEnAplicacionesWeb() {
        web.run(context -> assertThat(context).hasSingleBean(JrxPageController.class));
    }

    @Test
    @DisplayName("La propiedad jreactive.page-controller.enabled=false lo apaga")
    void seApagaConLaPropiedad() {
        web.withPropertyValues("jreactive.page-controller.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(JrxPageController.class));
    }

    @Test
    @DisplayName("Si la aplicación también lo registra al escanear el paquete, queda un solo endpoint")
    void noSeDuplicaEnAplicacionesQueEscaneanElPaquete() {
        web.withUserConfiguration(JrxPageController.class)
                .run(context -> assertThat(context).hasSingleBean(JrxPageController.class));
    }

    @Test
    @DisplayName("La propiedad también lo apaga en aplicaciones que escanean el paquete")
    void laPropiedadValeAlEscanearElPaquete() {
        web.withUserConfiguration(JrxPageController.class)
                .withPropertyValues("jreactive.page-controller.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(JrxPageController.class));
    }

    @Test
    @DisplayName("Fuera de una aplicación web no se registra")
    void noSeRegistraFueraDeAplicacionesWeb() {
        new ApplicationContextRunner()
                .withConfiguration(PAGE_CONTROLLER)
                .run(context -> assertThat(context).doesNotHaveBean(JrxPageController.class));
    }

    @Test
    @DisplayName("El starter declara la autoconfiguración del endpoint ante Spring Boot")
    void elStarterDeclaraLaAutoconfiguracion() {
        ImportCandidates candidates = ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader());

        assertThat(candidates.getCandidates()).contains(JReactivePageControllerAutoConfiguration.class.getName());
    }
}