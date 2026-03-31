package com.ciro.jreactive.router;

import com.ciro.jreactive.HtmlComponent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("RouteRegistry - Descubrimiento y Resolución de Rutas")
class RouteRegistryTest {

    // Componentes de prueba
    @Route(path = "/")
    public static class DummyRootPage extends HtmlComponent {
        @Override protected String template() { return "root"; }
    }

    @Route(path = "/users/{id}")
    public static class DummyUserPage extends HtmlComponent {
        @Override protected String template() { return "user"; }
    }

    @Test
    @DisplayName("Debe escanear componentes @Route y resolver las rutas dinámicas")
    void shouldScanAndResolveRoutes() {
        // Arrange
        ApplicationContext ctx = mock(ApplicationContext.class);
        AutowireCapableBeanFactory factory = mock(AutowireCapableBeanFactory.class);
        when(ctx.getAutowireCapableBeanFactory()).thenReturn(factory);
        when(ctx.getBeansWithAnnotation(org.springframework.boot.autoconfigure.SpringBootApplication.class))
                .thenReturn(Map.of());

        when(factory.createBean(DummyRootPage.class)).thenReturn(new DummyRootPage());
        when(factory.createBean(DummyUserPage.class)).thenReturn(new DummyUserPage());

        RouteRegistry registry = new RouteRegistry(ctx);

        // Act & Assert 1: Ruta raíz
        RouteProvider.Result rootRes = registry.resolve("/");
        assertThat(rootRes.component()).isInstanceOf(DummyRootPage.class);

        // Act & Assert 2: Ruta dinámica con parámetros
        RouteProvider.Result userRes = registry.resolve("/users/42");
        assertThat(userRes.component()).isInstanceOf(DummyUserPage.class);
        assertThat(userRes.params()).containsEntry("id", "42");

        // Act & Assert 3: Ruta inexistente (Fallback a raíz)
        RouteProvider.Result fallbackRes = registry.resolve("/not-found-page");
        assertThat(fallbackRes.component()).isInstanceOf(DummyRootPage.class);
    }
}