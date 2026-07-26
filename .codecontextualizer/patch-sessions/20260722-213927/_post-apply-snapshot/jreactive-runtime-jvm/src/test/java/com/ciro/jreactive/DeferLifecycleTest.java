package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Defer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Defer Lifecycle - Pruebas de Carga Asíncrona y Suspense")
class DeferLifecycleTest {

    public static class DeferredComponent extends HtmlComponent {
        @State public String heavyData = "Initial";
        @State public boolean isLoaded = false;

        @Defer("heavyData")
        public String loadHeavyData() {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            isLoaded = true;
            return "Loaded from DB";
        }

        @Override protected String template() { return "<div>{{heavyData}}</div>"; }
    }

    @Test
    @DisplayName("Debe ejecutar el método @Defer en un hilo virtual y actualizar el estado reactivo")
    void testDeferExecution() throws Exception {
        DeferredComponent comp = new DeferredComponent();
        comp.setId("defer-test-1");
        
        // _initIfNeeded lanza la tarea @Defer en un Virtual Thread
        comp._initIfNeeded();

        // Esperamos un máximo de 1s a que el hilo virtual termine su trabajo de fondo
        for (int i = 0; i < 20; i++) {
            if (comp.isLoaded) break;
            Thread.sleep(50);
        }

        assertThat(comp.isLoaded).isTrue();
        assertThat(comp.heavyData).isEqualTo("Loaded from DB");
        
        // Verificamos que el ciclo del Defer internamente forzó la sincronización (_syncState)
        Object rxData = comp.getRawBindings().get("heavyData").get();
        assertThat(rxData).isEqualTo("Loaded from DB");
    }

    @Test
    @DisplayName("Debe exponer el estado diferido y su fallback para Suspense")
    void testDeferSuspenseState() {
        DeferredComponent comp = new DeferredComponent();
        
        // Verifica que el framework sabe que "heavyData" es un estado diferido
        assertThat(comp._isDeferredState("heavyData")).isTrue();
        
        // Verifica que NO hay fallback definido en este caso (debería devolver null)
        assertThat(comp._getDeferFallback("heavyData")).isNull();
    }
}