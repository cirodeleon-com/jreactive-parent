package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.annotations.Stateless;
import com.ciro.jreactive.router.Layout;
import com.ciro.jreactive.router.UrlVariable;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("JrxHttpApi - Pruebas de Interfaz HTTP seguras")
class JrxHttpApiTest {

    @Mock PageResolver pageResolver;
    @Mock CallGuard guard;
    @Mock JrxHubManager hubManager;
    private JrxHttpApi api;
    private ObjectMapper mapper = new ObjectMapper();

    public static class SimpleLayout extends HtmlComponent {
        @Override protected String template() { return "<main><slot/></main>"; }
    }

    @Layout(SimpleLayout.class)
    public static class LayoutPage extends HtmlComponent {
        @Override protected String template() { return "<h1>Contenido</h1>"; }
    }

    @Stateless
    public static class ActionPage extends HtmlComponent {
        @State public int valor = 0;
        @UrlVariable("ruta_id") public String urlId;
        
        @Call public void sumar(int input) { valor += input; }
        @Override protected String template() { return "<div>{{valor}}</div>"; }
    }

    @BeforeEach
    void setUp() {
        AstComponentEngine.installAsDefault();
        api = new JrxHttpApi(pageResolver, mapper, guard, true, hubManager);
    }

    @Test
    @DisplayName("Debe renderizar la página con su layout inyectado")
    void testRenderWithLayout() {
        LayoutPage page = new LayoutPage();
        lenient().when(pageResolver.getPage(anyString(), anyString(), any())).thenReturn(page);

        String html = api.render("sid", "/home", true, new HashMap<>());
        
        // Verifica que inyectó el slot del layout y el scope de CSS
        assertThat(html).contains("<main");
        assertThat(html).contains("class=\"jrx-sc-LayoutPage\"");
        assertThat(html).contains("Contenido");
    }

    @Test
    @DisplayName("Debe renderizar un Request Parcial (SPA) sin Layout")
    void testRenderPartialNoLayout() {
        LayoutPage page = new LayoutPage();
        lenient().when(pageResolver.getPage(anyString(), anyString(), any())).thenReturn(page);

        // renderLayout = false
        String html = api.render("sid", "/home", false, new HashMap<>());
        
        assertThat(html).doesNotContain("<main>"); // El layout no debe estar
        assertThat(html).contains("Contenido");
    }

    @Test
    @DisplayName("Debe resolver y ejecutar @Call vía alias (Ref)")
    void testCallMethodAliasResolution() {
        ActionPage page = new ActionPage() {
            @Override
            public String _resolveRef(String alias) {
                if ("miHijo".equals(alias)) return "page1";
                return null;
            }
        };
        page.setId("page1");
        page._initIfNeeded();
        
        lenient().when(pageResolver.getPage(anyString(), anyString(), any())).thenReturn(page);
        lenient().when(guard.tryConsume(anyString())).thenReturn(true);
        lenient().when(guard.validateParams(any(), any(), any())).thenReturn(Set.of());

        Map<String, Object> body = Map.of("args", List.of(20));
        String jsonResponse = api.call("sid", "/action", "miHijo.sumar", body, new HashMap<>());

        assertThat(page.valor).isEqualTo(20);
        assertThat(jsonResponse).contains("\"ok\":true");
    }

    @Test
    @DisplayName("Debe inyectar parámetros de URL en el @Call")
    void testUrlVariableInjection() {
        ActionPage page = new ActionPage();
        page.setId("page1");
        
        lenient().when(pageResolver.getPage(anyString(), anyString(), any())).thenReturn(page);
        lenient().when(pageResolver.getParams(anyString(), anyString())).thenReturn(Map.of("ruta_id", "ABC-123"));
        lenient().when(guard.tryConsume(anyString())).thenReturn(true);
        lenient().when(guard.validateParams(any(), any(), any())).thenReturn(Set.of());

        Map<String, Object> body = Map.of("args", List.of(5));
        api.call("sid", "/action", "sumar", body, new HashMap<>());

        assertThat(page.valor).isEqualTo(5);
    }
    
    @Test
    @DisplayName("Debe manejar llamadas a métodos inexistentes retornando un JSON de error")
    void testMethodNotFound() {
        ActionPage page = new ActionPage();
        page.setId("page1");
        
        lenient().when(pageResolver.getPage(anyString(), anyString(), any())).thenReturn(page);
        lenient().when(guard.tryConsume(anyString())).thenReturn(true);
        lenient().when(guard.errorJson(anyString(), anyString())).thenReturn("{\"ok\":false,\"code\":\"NOT_FOUND\"}");

        Map<String, Object> body = Map.of("args", List.of());
        String res = api.call("sid", "/action", "metodoFantasma", body, new HashMap<>());

        assertThat(res).contains("NOT_FOUND");
    }
    
    @Test
    @DisplayName("Debe capturar y desempaquetar excepciones lanzadas dentro de un @Call")
    void testCallMethodThrowsException() {
        ActionPage page = new ActionPage() {
            @Call public void explotar() { 
                throw new IllegalArgumentException("Error de negocio simulado"); 
            }
        };
        page.setId("pageError");
        page._initIfNeeded();
        
        lenient().when(pageResolver.getPage(anyString(), anyString(), any())).thenReturn(page);
        lenient().when(guard.tryConsume(anyString())).thenReturn(true);
        lenient().when(guard.validateParams(any(), any(), any())).thenReturn(Set.of());
        // Simulamos que el guard formatea el error
        lenient().when(guard.errorJson(anyString(), anyString())).thenReturn("{\"ok\":false,\"error\":\"Error de negocio simulado\"}");

        Map<String, Object> body = Map.of("args", java.util.List.of());
        
        // Llamamos a un método que va a explotar a propósito
        String jsonResponse = api.call("sid", "/action", "explotar", body, new java.util.HashMap<>());

        // Debe haber pasado por el catch y usado el guard para devolver el JSON
        assertThat(jsonResponse).contains("Error de negocio simulado");
    }
    
    @Test
    @DisplayName("Debe generar deltas precisos (JsonNode diffing) para componentes @Stateless")
    void testStatelessDeltaDiffing() throws Exception {
        ActionPage page = new ActionPage();
        page.setId("page_stateless");
        page._initIfNeeded();

        // Simulamos el estado viejo viniendo en el token
        Map<String, Object> oldState = new HashMap<>();
        oldState.put("valor", 0);
        String fakeToken = JrxStateToken.encode(oldState);

        lenient().when(pageResolver.getPage(anyString(), anyString(), any())).thenReturn(page);
        lenient().when(guard.tryConsume(anyString())).thenReturn(true);
        lenient().when(guard.validateParams(any(), any(), any())).thenReturn(Set.of());

        // Hacemos el call enviando el token (mochila)
        Map<String, Object> body = Map.of(
            "args", List.of(42),
            "stateToken", fakeToken
        );

        String response = api.call("sid", "/action", "sumar", body, new HashMap<>());

        // Assert: Al ser @Stateless, debe devolver el nuevo token y el batch con los deltas calculados
        assertThat(response).contains("\"newStateToken\"");
        assertThat(response).contains("\"batch\"");
        assertThat(response).contains("\"v\":42"); // Delta detectó el cambio de 0 a 42
    }

    @Test
    @DisplayName("Debe capturar errores de Invocación internos y extraer la causa raíz")
    void testInvocationErrorExtraction() {
        ActionPage page = new ActionPage() {
            @Call public void explotarFuerte() { 
                throw new NullPointerException(); // Error sin mensaje
            }
        };
        page._initIfNeeded();
        lenient().when(pageResolver.getPage(anyString(), anyString(), any())).thenReturn(page);
        lenient().when(guard.tryConsume(anyString())).thenReturn(true);
        lenient().when(guard.errorJson(eq("INVOKE_ERROR"), anyString())).thenReturn("ERROR_CAPTURADO");

        String response = api.call("sid", "/action", "explotarFuerte", Map.of(), new HashMap<>());
        assertThat(response).isEqualTo("ERROR_CAPTURADO");
    }
}