package com.ciro.jreactive;

import com.ciro.jreactive.router.RouteProvider;
import com.ciro.jreactive.store.StateStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator; // <-- 1. Agrega este import
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("JReactiveAutoConfiguration - Carga de Beans de Spring")
class JReactiveAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JReactiveAutoConfiguration.class, 
                    JacksonConfig.class
            ))
            .withUserConfiguration(MockConfig.class); 

    @Configuration
    static class MockConfig {
        @Bean
        public RouteProvider routeProvider() {
            return mock(RouteProvider.class); 
        }

        // 🔥 EL FIX: Simulamos el Validator que Spring Boot normalmente proveería
        @Bean
        public Validator validator() {
            return mock(Validator.class);
        }
    }

    @Test
    @DisplayName("Debe registrar todos los beans core de JReactive por defecto")
    void shouldRegisterDefaultBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(StateStore.class);
            assertThat(context).hasSingleBean(CallGuard.class);
            assertThat(context).hasSingleBean(PageResolver.class);
            assertThat(context).hasSingleBean(JrxHubManager.class);
            assertThat(context).hasSingleBean(com.ciro.jreactive.spi.JrxMessageBroker.class);
        });
    }

    @Test
    @DisplayName("No debe sobrescribir el StateStore si el usuario define el suyo")
    void shouldBackOffWhenUserDefinesStateStore() {
        contextRunner.withUserConfiguration(CustomStoreConfig.class).run(context -> {
            assertThat(context).hasSingleBean(StateStore.class);
            assertThat(context).getBean("customStore").isSameAs(context.getBean(StateStore.class));
        });
    }

    @Configuration
    static class CustomStoreConfig {
        @Bean(name = "customStore")
        public StateStore customStore() {
            return mock(StateStore.class);
        }
    }
}