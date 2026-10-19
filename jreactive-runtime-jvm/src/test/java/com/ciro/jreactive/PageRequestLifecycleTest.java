package com.ciro.jreactive;

import com.ciro.jreactive.annotations.StatefulRam;
import com.ciro.jreactive.router.RouteProvider;
import com.ciro.jreactive.store.CaffeineStateStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("HttpPage - La página decide su respuesta HTTP")
class PageRequestLifecycleTest {

    /** Página que carga su dato en cada petición y responde 202 mientras tanto. */
    public static class LicensePage extends HtmlComponent implements HttpPage {
        final List<String> events = new ArrayList<>();

        @State
        public String message = "esperando";

        @Override
        protected void onInit() {
            events.add("init");
        }

        @Override
        public void onRequest(JrxResponse response) {
            events.add("request");
            message = "licencia lista";
            response.status(202).header("Referrer-Policy", "no-referrer");
        }

        @Override
        protected String template() {
            return "<p>{{message}}</p>";
        }
    }

    /** Página que siempre rechaza la petición. */
    public static class MissingPage extends HtmlComponent implements HttpPage {
        @Override
        public void onRequest(JrxResponse response) {
            throw new JrxHttpException(404, "No existe");
        }

        @Override
        protected String template() {
            return "<p>nunca se muestra</p>";
        }
    }

    /** Página con estado en RAM: se conserva entre peticiones de la misma sesión. */
    @StatefulRam
    public static class CounterPage extends HtmlComponent implements HttpPage {
        final List<String> events = new ArrayList<>();

        @Override
        protected void onInit() {
            events.add("init");
        }

        @Override
        public void onRequest(JrxResponse response) {
            events.add("request");
        }

        @Override
        protected String template() {
            return "<p>contador</p>";
        }
    }

    /** Página que no implementa HttpPage: su ciclo de vida no cambia. */
    public static class PlainPage extends HtmlComponent {
        final List<String> events = new ArrayList<>();

        @Override
        protected void onInit() {
            events.add("init");
        }

        @Override
        protected String template() {
            return "<p>normal</p>";
        }
    }

    @BeforeAll
    static void installEngine() {
        AstComponentEngine.installAsDefault();
    }

    @Test
    @DisplayName("En una petición de página, onRequest corre después de onInit y antes del render")
    void onRequestCorreDespuesDeOnInitYAntesDelRender() {
        AtomicReference<LicensePage> created = new AtomicReference<>();
        JrxHttpApi api = apiFor(() -> remember(created, new LicensePage()));
        JrxResponse response = new JrxResponse();

        String html = api.forRequest(response).render("sid", "/licencia", false, Map.of());

        assertThat(created.get().events).containsExactly("init", "request");
        assertThat(html).contains("licencia lista");
        assertThat(response.status()).isEqualTo(202);
        assertThat(response.headers()).containsEntry("Referrer-Policy", "no-referrer");
    }

    @Test
    @DisplayName("Un render sin petición HTTP de página, como el de un @Call, no llama a onRequest")
    void elRenderNormalNoLlamaAOnRequest() {
        AtomicReference<LicensePage> created = new AtomicReference<>();
        JrxHttpApi api = apiFor(() -> remember(created, new LicensePage()));

        String html = api.render("sid", "/licencia", false, Map.of());

        assertThat(created.get().events).containsExactly("init");
        assertThat(html).contains("esperando");
    }

    @Test
    @DisplayName("La página puede cortar la petición con un código de error")
    void laPaginaPuedeCortarLaPeticion() {
        JrxHttpApi api = apiFor(MissingPage::new);

        assertThatThrownBy(() -> api.forRequest(new JrxResponse()).render("sid", "/falta", false, Map.of()))
                .isInstanceOfSatisfying(JrxHttpException.class, e -> assertThat(e.status()).isEqualTo(404));
    }

    @Test
    @DisplayName("En una página con estado, onInit corre una vez y onRequest en cada petición")
    void conEstadoOnRequestCorreEnCadaPeticion() {
        AtomicReference<CounterPage> created = new AtomicReference<>();
        JrxHttpApi api = apiFor(() -> remember(created, new CounterPage()));

        api.forRequest(new JrxResponse()).render("sid", "/contador", false, Map.of());
        api.forRequest(new JrxResponse()).render("sid", "/contador", false, Map.of());

        assertThat(created.get().events).containsExactly("init", "request", "request");
    }

    @Test
    @DisplayName("Una página que no implementa HttpPage responde 200, sin cabeceras propias y con su ciclo de siempre")
    void unaPaginaNormalNoCambia() {
        AtomicReference<PlainPage> created = new AtomicReference<>();
        JrxHttpApi api = apiFor(() -> remember(created, new PlainPage()));
        JrxResponse response = new JrxResponse();

        String html = api.forRequest(response).render("sid", "/normal", false, Map.of());

        assertThat(html).contains("normal");
        assertThat(created.get().events).containsExactly("init");
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.headers()).isEmpty();
    }

    private static JrxHttpApi apiFor(Supplier<HtmlComponent> pages) {
        RouteProvider routes = path -> new RouteProvider.Result(pages.get(), Map.of());
        PageResolver resolver = new PageResolver(routes, new CaffeineStateStore());
        return new JrxHttpApi(resolver, new ObjectMapper(), null, false, null);
    }

    private static <T extends HtmlComponent> T remember(AtomicReference<T> holder, T page) {
        holder.set(page);
        return page;
    }
}