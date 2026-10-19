package com.ciro.jreactive;

import com.ciro.jreactive.router.RouteProvider;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("JReactiveAutoConfiguration - Registro del starter sin escaneo del framework")
class JReactiveAutoConfigurationRegistrationTest {

    @Configuration(proxyBeanMethods = false)
    static class HostConfig {

        @Bean
        RouteProvider routeProvider() {
            return mock(RouteProvider.class);
        }

        @Bean
        Validator validator() {
            return mock(Validator.class);
        }
    }

    @Test
    @DisplayName("El starter publica su autoconfiguración en el registro de Spring Boot")
    void elStarterPublicaSuAutoconfiguracion() {
        assertThat(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()).getCandidates())
                .contains(JReactiveAutoConfiguration.class.getName());
    }

    @Test
    @DisplayName("Una aplicación web que no escanea el framework obtiene sus piezas web")
    void aplicacionWebSinEscaneoObtienePiezasWeb() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JReactiveAutoConfiguration.class))
                .withUserConfiguration(HostConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(WsConfig.class);
                    assertThat(context).hasSingleBean(JrxRequestQueue.class);
                    assertThat(context).hasSingleBean(DelegatingWebSocketHandler.class);
                    assertThat(context).hasSingleBean(RouteProvider.class);
                });
    }

    @Test
    @DisplayName("Fuera de una aplicación web solo se registra el núcleo")
    void sinServidorWebSoloSeRegistraElNucleo() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JReactiveAutoConfiguration.class))
                .withUserConfiguration(HostConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PageResolver.class);
                    assertThat(context).doesNotHaveBean(JrxRequestQueue.class);
                    assertThat(context).doesNotHaveBean(DelegatingWebSocketHandler.class);
                });
    }
}