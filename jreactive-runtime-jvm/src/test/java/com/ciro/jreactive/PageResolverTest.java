package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Stateful;
import com.ciro.jreactive.annotations.Stateless;
import com.ciro.jreactive.router.RouteProvider;
import com.ciro.jreactive.store.StateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PageResolver - Enrutamiento y Gestión de Memoria")
class PageResolverTest {

    @Mock RouteProvider registry;
    @Mock StateStore store;

    @InjectMocks PageResolver resolver;

    @Stateful
    static class DummyPage extends HtmlComponent {
        public int paramsInyectados = 0;
        @Override protected String template() { return ""; }
        @Override void _injectParams(Map<String, String> params) { paramsInyectados++; }
        @Override void _injectQueryParams(Map<String, String> params) { paramsInyectados++; }
    }

    
    static class StatelessPage extends HtmlComponent {
        @Override protected String template() { return ""; }
    }

    @Test
    @DisplayName("Cache Miss: Debe instanciar y guardar la página en el Store si no existe")
    void testGetPageCacheMiss() {
        DummyPage nuevaPagina = new DummyPage();
        
        // Simulamos que NO está en la RAM
        when(store.get("sid", "/home")).thenReturn(null);
        // Simulamos que el Router encuentra la clase y le pasa params
        when(registry.resolve("/home")).thenReturn(new RouteProvider.Result(nuevaPagina, Map.of("id", "10")));

        HtmlComponent result = resolver.getPage("sid", "/home");

        assertThat(result).isSameAs(nuevaPagina);
        assertThat(result.getId()).isEqualTo("page_home"); // El ID generado debe ser seguro
        
        // Verificamos que se guardó en la memoria RAM
        verify(store).put(eq("sid"), eq("/home"), eq(nuevaPagina));
        assertThat(nuevaPagina.paramsInyectados).isEqualTo(1); // Se inyectaron los params del path
    }

    @Test
    @DisplayName("Cache Hit: Debe recuperar la página directo de la RAM sin llamar al Router")
    void testGetPageCacheHit() {
        DummyPage paginaEnRam = new DummyPage();
        
        // Simulamos que la página SÍ está en la memoria
        when(store.get("sid", "/home")).thenReturn(paginaEnRam);

        HtmlComponent result = resolver.getPage("sid", "/home");

        assertThat(result).isSameAs(paginaEnRam);
        
        // Verificamos que NO le preguntamos al Router (Ahorro de CPU)
        verify(registry, never()).resolve(anyString()); 
    }

    @Test
    @DisplayName("Stateless: Un componente @Stateless debe borrarse de la RAM tras ser recuperado")
    void testStatelessComponent() {
        StatelessPage stateless = new StatelessPage();
        
        // 1. Simulamos que estaba "atascado" en la RAM
        when(store.get("sid", "/login")).thenReturn(stateless);
        
        // 2. 🔥 FIX: Le enseñamos al Router qué devolver cuando se vea forzado a recrearlo
        when(registry.resolve("/login")).thenReturn(new RouteProvider.Result(stateless, Map.of()));

        HtmlComponent result = resolver.getPage("sid", "/login");

        assertThat(result).isNotNull();
        
        // 3. Verificamos que tu código cumplió su palabra y lo borró de la RAM
        verify(store).remove("sid", "/login");
    }

    @Test
    @DisplayName("Debe delegar correctamente el borrado de sesiones y persistencia")
    void testDelegations() {
        DummyPage page = new DummyPage();
        
        resolver.persist("sid", "/home", page);
        verify(store).put("sid", "/home", page);

        resolver.evict("sid", "/home");
        verify(store).remove("sid", "/home");

        resolver.evictAll("sid");
        verify(store).removeSession("sid");
    }
    
    @Test
    @DisplayName("Debe obtener parámetros de ruta y la instancia de Home directamente")
    void testGetParamsAndHome() {
        DummyPage homePage = new DummyPage();
        
        // Simulamos la resolución de rutas
        when(registry.resolve("/ruta")).thenReturn(new RouteProvider.Result(homePage, Map.of("clave", "valor")));
        when(registry.resolve("/")).thenReturn(new RouteProvider.Result(homePage, Map.of()));

        // Act & Assert 1: getParams
        Map<String, String> params = resolver.getParams("sid", "/ruta");
        assertThat(params).containsEntry("clave", "valor");

        // Act & Assert 2: getHomePageInstance
        HtmlComponent home = resolver.getHomePageInstance("sid");
        assertThat(home).isNotNull();
    }
}