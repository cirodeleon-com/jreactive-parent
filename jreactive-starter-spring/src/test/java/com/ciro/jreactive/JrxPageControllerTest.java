package com.ciro.jreactive;

import com.ciro.jreactive.spi.AuthorizationProvider;
import com.ciro.jreactive.spi.AuthorizationProvider.AuthContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("JrxPageController - Páginas y @Call servidos por el starter")
class JrxPageControllerTest {

    private final RecordingApi api = new RecordingApi();
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new JrxPageController(api, new JrxRequestQueue()))
            .build();

    @Test
    @DisplayName("Una página se sirve completa, sin caché y con los parámetros de la URL")
    void sirveLaPaginaCompletaSinCache() throws Exception {
        mvc.perform(get("/precios").param("plan", "pro"))
                .andExpect(status().isOk())
                .andExpect(content().string("<main>/precios</main>"))
                .andExpect(header().string("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("Expires", "0"));

        assertThat(api.path).isEqualTo("/precios");
        assertThat(api.renderLayout).isTrue();
        assertThat(api.query).containsEntry("plan", "pro");
        assertThat(api.sessionId).isNotBlank();
    }

    @Test
    @DisplayName("La raíz se sirve como página")
    void sirveLaRaiz() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());

        assertThat(api.path).isEqualTo("/");
    }

    @Test
    @DisplayName("La navegación parcial pide la página sin su layout")
    void laNavegacionParcialPideLaPaginaSinLayout() throws Exception {
        mvc.perform(get("/precios").header("X-Partial", "1")).andExpect(status().isOk());

        assertThat(api.renderLayout).isFalse();
    }

    @Test
    @DisplayName("Las rutas de recursos, WebSocket, endpoints internos y API de la aplicación no se sirven como páginas")
    void noSirveComoPaginasLasRutasReservadas() throws Exception {
        List<String> reservadas = List.of(
                "/js/jreactive-runtime.js", "/css/site.css", "/static/logo.png",
                "/ws/info", "/jrx/otra", "/api/founder/claim");

        for (String ruta : reservadas) {
            mvc.perform(get(ruta)).andExpect(status().isNotFound());
        }

        assertThat(api.renders).isZero();
    }

    @Test
    @DisplayName("Un @Call llega a la página que indica el navegador, con su cuerpo y los parámetros de la URL")
    void elCallLlegaALaPaginaIndicadaPorElNavegador() throws Exception {
        mvc.perform(post("/call/Contador-1.sumar")
                        .param("plan", "pro")
                        .header("X-Path", "/precios?ref=menu#planes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"args\":[2]}"))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"ok\":true}"));

        assertThat(api.qualified).isEqualTo("Contador-1.sumar");
        assertThat(api.path).isEqualTo("/precios");
        assertThat(api.body).containsEntry("args", List.of(2));
        assertThat(api.query).containsEntry("plan", "pro");
    }

    @Test
    @DisplayName("Sin cabecera de ruta, el @Call va a la página desde la que se envió")
    void sinCabeceraDeRutaUsaLaPaginaDeOrigen() throws Exception {
        mvc.perform(post("/call/sumar")
                        .header("Referer", "https://cirodeleon.com/licencia?id=7#arriba")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        assertThat(api.path).isEqualTo("/licencia");
    }

    @Test
    @DisplayName("Sin cabecera de ruta ni página de origen, el @Call va a la raíz")
    void sinOrigenElCallVaALaRaiz() throws Exception {
        mvc.perform(post("/call/sumar").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        assertThat(api.path).isEqualTo("/");
    }

    @Test
    @DisplayName("El @Call se ejecuta con la identidad y los roles del usuario")
    void elCallLlevaLaIdentidadDelUsuario() throws Exception {
        mvc.perform(post("/call/borrar")
                        .principal(new JrxAuthContextResolverTest.RolesPrincipal("ana", List.of("ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        assertThat(api.auth.principalName()).isEqualTo("ana");
        assertThat(api.auth.roles()).containsExactly("ADMIN");
    }

    @Test
    @DisplayName("Sin usuario autenticado, el @Call se ejecuta como anónimo")
    void sinUsuarioElCallEsAnonimo() throws Exception {
        mvc.perform(post("/call/borrar").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        assertThat(api.auth.isAuthenticated()).isFalse();
    }

    @Test
    @DisplayName("La página decide su código de estado y sus cabeceras reemplazan a las de por defecto")
    void laPaginaDecideSuCodigoYSusCabeceras() throws Exception {
        api.onRequest = response -> response.status(202)
                .header("Referrer-Policy", "no-referrer")
                .header("Cache-Control", "private, max-age=60");

        mvc.perform(get("/licencia"))
                .andExpect(status().isAccepted())
                .andExpect(content().string("<main>/licencia</main>"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Cache-Control", "private, max-age=60"))
                .andExpect(header().string("Pragma", "no-cache"));
    }

    @Test
    @DisplayName("Si la página corta la petición con un error, se responde con ese código")
    void laPaginaPuedeResponderConUnError() throws Exception {
        api.onRequest = response -> {
            throw new JrxHttpException(404, "No existe esa licencia");
        };

        mvc.perform(get("/licencia/desconocida")).andExpect(status().isNotFound());
    }

    /** Sustituye el render y la ejecución reales: estas pruebas miran solo la parte HTTP. */
    static final class RecordingApi extends JrxHttpApi {

        String sessionId;
        String path;
        boolean renderLayout;
        Map<String, String> query;
        String qualified;
        Map<String, Object> body;
        AuthContext auth;
        int renders;
        JrxResponse response;
        /** Lo que hace la página simulada en onRequest; por defecto, nada. */
        java.util.function.Consumer<JrxResponse> onRequest = ignored -> { };

        RecordingApi() {
            super(null, null, null, false, null);
        }

        @Override
        public JrxHttpApi forRequest(JrxResponse response) {
            this.response = response;
            return this;
        }

        @Override
        public String render(String sessionId, String path, boolean renderLayout, Map<String, String> queryParams) {
            this.sessionId = sessionId;
            this.path = path;
            this.renderLayout = renderLayout;
            this.query = queryParams;
            this.renders++;
            onRequest.accept(response);
            return "<main>" + path + "</main>";
        }

        @Override
        public String call(String sessionId, String path, String qualified,
                           Map<String, Object> body, Map<String, String> queryParams) {
            this.sessionId = sessionId;
            this.path = path;
            this.qualified = qualified;
            this.body = body;
            this.query = queryParams;
            this.auth = AuthorizationProvider.Holder.get();
            return "{\"ok\":true}";
        }
    }
}