package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.annotations.Defer;
import com.ciro.jreactive.annotations.OnEvent;
import com.ciro.jreactive.annotations.StatefulRam;
import com.ciro.jreactive.router.RouteProvider;
import com.ciro.jreactive.store.CaffeineStateStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PageInteractivity - Qué páginas necesitan su estado en el navegador")
class PageInteractivityTest {

    private static final Pattern STATE_META = Pattern.compile("<meta name=\"jrx-state\" content=\"([^\"]+)\">");

    /** Solo muestra un dato: no necesita su estado en el navegador. */
    public static class DisplayPage extends HtmlComponent {
        @State
        public String license = "LIC-SECRETA-123";

        @Override
        protected String template() {
            return "<p>{{license}}</p>";
        }
    }

    public static class ActionPage extends HtmlComponent {
        @State
        public int count = 0;

        @Call
        public void add() {
            count++;
        }

        @Override
        protected String template() {
            return "<p>{{count}}</p>";
        }
    }

    public static class InheritedActionPage extends ActionPage {
    }

    public static class DeferPage extends HtmlComponent {
        @State
        public String data;

        @Defer("data")
        public String load() {
            return "listo";
        }
    }

    public static class EventPage extends HtmlComponent {
        @OnEvent("pedido-listo")
        public void refresh() {
        }
    }

    public static class BindPage extends HtmlComponent {
        @Bind
        public Type<String> name = Type.$("Ana");
    }

    public static class TimerPage extends HtmlComponent {
        @Override
        protected void onMount() {
        }
    }

    @StatefulRam
    public static class RamPage extends HtmlComponent {
    }

    @BeforeAll
    static void installEngine() {
        AstComponentEngine.installAsDefault();
    }

    @Test
    @DisplayName("Una página que solo muestra @State no es interactiva")
    void soloMostrarNoEsInteractivo() {
        assertThat(PageInteractivity.isLiveClass(DisplayPage.class)).isFalse();
        assertThat(PageInteractivity.isInteractive(new DisplayPage(), null, "<p>hola</p>")).isFalse();
    }

    @Test
    @DisplayName("@Call, @Defer, @OnEvent, @Bind, onMount o estado en servidor la hacen interactiva, también heredados")
    void lasMarcasVivasLaHacenInteractiva() {
        assertThat(PageInteractivity.isLiveClass(ActionPage.class)).isTrue();
        assertThat(PageInteractivity.isLiveClass(InheritedActionPage.class)).isTrue();
        assertThat(PageInteractivity.isLiveClass(DeferPage.class)).isTrue();
        assertThat(PageInteractivity.isLiveClass(EventPage.class)).isTrue();
        assertThat(PageInteractivity.isLiveClass(BindPage.class)).isTrue();
        assertThat(PageInteractivity.isLiveClass(TimerPage.class)).isTrue();
        assertThat(PageInteractivity.isLiveClass(RamPage.class)).isTrue();
    }

    @Test
    @DisplayName("Un hijo interactivo hace interactiva a la página")
    void unHijoInteractivoLaHaceInteractiva() {
        DisplayPage page = new DisplayPage();
        page._addChild(new ActionPage());

        assertThat(PageInteractivity.isInteractive(page, null, "<p>hola</p>")).isTrue();
    }

    @Test
    @DisplayName("Bloques del navegador, scripts propios y campos con name hacen interactiva la página")
    void elHtmlQueSeResuelveEnElNavegador() {
        DisplayPage page = new DisplayPage();

        assertThat(PageInteractivity.isInteractive(page, null, "<template data-if=\"listo\"></template>")).isTrue();
        assertThat(PageInteractivity.isInteractive(page, null, "<template data-each=\"items:it\"></template>")).isTrue();
        assertThat(PageInteractivity.isInteractive(page, null, "<script data-resource=\"Mapa\">x</script>")).isTrue();
        assertThat(PageInteractivity.isInteractive(page, null, "<INPUT type=\"text\" Name=\"q\">")).isTrue();
        assertThat(PageInteractivity.isInteractive(page, null, "<input id=\"email\" readonly value=\"a@b.co\">")).isFalse();
    }

    @Test
    @DisplayName("Una página que solo muestra datos no los repite en el estado del HTML y conserva el modo sin WebSocket")
    void laPaginaQueSoloMuestraNoIncrustaSuEstado() throws Exception {
        String html = apiFor(DisplayPage.class).render("sid", "/licencia", false, Map.of());

        assertThat(html).containsOnlyOnce("LIC-SECRETA-123");
        assertThat(JrxStateToken.decode(stateToken(html))).isEmpty();
    }

    @Test
    @DisplayName("Una página interactiva sigue llevando su estado al navegador")
    void laPaginaInteractivaLlevaSuEstado() throws Exception {
        String html = apiFor(ActionPage.class).render("sid", "/contador", false, Map.of());

        assertThat(JrxStateToken.decode(stateToken(html))).containsKey("count");
    }

    private static JrxHttpApi apiFor(Class<? extends HtmlComponent> pageType) {
        RouteProvider routes = path -> new RouteProvider.Result(newPage(pageType), Map.of());
        return new JrxHttpApi(new PageResolver(routes, new CaffeineStateStore()), new ObjectMapper(), null, false, null);
    }

    private static HtmlComponent newPage(Class<? extends HtmlComponent> pageType) {
        try {
            return pageType.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String stateToken(String html) {
        Matcher meta = STATE_META.matcher(html);
        assertThat(meta.find()).as("la página conserva la marca de estado para el runtime").isTrue();
        return meta.group(1);
    }
}