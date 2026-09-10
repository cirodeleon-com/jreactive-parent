package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Defer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("@Defer - Endurecimiento: Cancelación y Anti-Duplicados")
class DeferLifecycleTest {

    @BeforeAll
    static void setup() {
        AstComponentEngine.installAsDefault();
    }

    static class DeferPage extends HtmlComponent {
        @State public List<String> fastData;
        @State public List<String> slowData;
        @State public List<String> errorData;
        @State public List<String> timeoutData;
        final AtomicInteger slowCallCount = new AtomicInteger(0);

        @Defer(value = "fastData")
        public List<String> loadFast() {
            return List.of("A", "B");
        }

        @Defer(value = "slowData")
        public List<String> loadSlow() throws InterruptedException {
            slowCallCount.incrementAndGet();
            Thread.sleep(300);
            return List.of("C");
        }

        @Defer(value = "errorData", errorFallback = "<div class='error'>Error cargando datos</div>")
        public List<String> loadWithError() {
            throw new RuntimeException("Fallo simulado");
        }

        @Defer(value = "timeoutData", timeout = 100, errorFallback = "<div class='timeout'>Timeout</div>")
        public List<String> loadWithTimeout() throws InterruptedException {
            Thread.sleep(500);
            return List.of("T");
        }

        @Override
        protected String template() { return "<div>{{fastData.size}}</div>"; }
    }

    @Test
    @DisplayName("Éxito: la tarea diferida carga datos correctamente")
    void testDeferSuccess() throws Exception {
        DeferPage page = new DeferPage();
        page._initIfNeeded();
        page._mountRecursive();

        Thread.sleep(500);

        // Validamos la memoria reactiva (fuente de verdad) en lugar del campo físico 
        // para evitar condiciones de carrera si el hilo del test completa antes que el activeGuard del componente se habilite.
        Object fastData = page.getRawBindings().get("fastData").get();
        assertThat(fastData).isEqualTo(List.of("A", "B"));
    }

    @Test
    @DisplayName("Cancelación: al desmontar, el estado es UNMOUNTED")
    void testDeferCancellation() throws Exception {
        DeferPage page = new DeferPage();
        page._initIfNeeded();
        page._mountRecursive();

        page._unmountRecursive();

        Thread.sleep(500);

        assertThat(page._state()).isEqualTo(ComponentState.UNMOUNTED);
    }

    @Test
    @DisplayName("Anti-Duplicados: reloadDeferred no lanza tarea duplicada si ya está corriendo")
    void testDeferAntiDuplicate() throws Exception {
        DeferPage page = new DeferPage();
        page._initIfNeeded();
        page._mountRecursive();

        // La carga inicial de slowData ya está corriendo (sleep 300ms)
        Thread.sleep(100);

        // Intentamos recargar mientras la inicial sigue corriendo
        page.reloadDeferred("slowData");
        page.reloadDeferred("slowData");

        // Esperamos a que todo termine
        Thread.sleep(600);

        // Solo debe haberse ejecutado una vez (la inicial); los 2 reloads fueron ignorados
        assertThat(page.slowCallCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Error: la tarea diferida que falla inyecta el centinela de error en el estado reactivo")
    void testDeferErrorVisible() throws Exception {
        DeferPage page = new DeferPage();
        page._initIfNeeded();
        page._mountRecursive();

        Thread.sleep(500);

        // El centinela Map debe estar en el ReactiveVar con el flag booleano
        Object errorData = page.getRawBindings().get("errorData").get();
        assertThat(errorData).isInstanceOf(java.util.Map.class);
        @SuppressWarnings("unchecked")
        java.util.Map<String, String> errorMarker = (java.util.Map<String, String>) errorData;
        assertThat(errorMarker.get("__jrx_defer_error__")).isEqualTo("true");
    }

    @Test
    @DisplayName("Timeout: la tarea diferida que excede el timeout inyecta el centinela de error")
    void testDeferTimeoutErrorVisible() throws Exception {
        DeferPage page = new DeferPage();
        page._initIfNeeded();
        page._mountRecursive();

        // timeout=100ms pero la tarea duerme 500ms → timeout dispara primero
        Thread.sleep(700);

        Object timeoutData = page.getRawBindings().get("timeoutData").get();
        assertThat(timeoutData).isInstanceOf(java.util.Map.class);
        @SuppressWarnings("unchecked")
        java.util.Map<String, String> errorMarker = (java.util.Map<String, String>) timeoutData;
        assertThat(errorMarker.get("__jrx_defer_error__")).isEqualTo("true");
    }

    @Test
    @DisplayName("_getDeferErrorFallback devuelve el HTML configurado, vacío o null según corresponda")
    void testGetDeferErrorFallbackDirect() {
        DeferPage page = new DeferPage();
        page._initIfNeeded();

        // stateKey con errorFallback definido → devuelve el HTML exacto
        assertThat(page._getDeferErrorFallback("errorData"))
            .isEqualTo("<div class='error'>Error cargando datos</div>");

        assertThat(page._getDeferErrorFallback("timeoutData"))
            .isEqualTo("<div class='timeout'>Timeout</div>");

        // stateKey con @Defer pero sin errorFallback → devuelve string vacío (no null)
        assertThat(page._getDeferErrorFallback("fastData")).isEmpty();
        assertThat(page._getDeferErrorFallback("slowData")).isEmpty();

        // stateKey que no tiene ningún @Defer asociado → devuelve null
        assertThat(page._getDeferErrorFallback("nonExistent")).isNull();
    }
}