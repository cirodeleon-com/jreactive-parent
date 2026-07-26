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
}