package com.ciro.jreactive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ciro.jreactive.annotations.Prop;
import com.ciro.jreactive.annotations.Shared;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("HtmlComponent Unit Tests (Fundacional)")
class HtmlComponentTest {

    // A. EL COMPONENTE DE PRUEBAS MASIVO (MOCK)
    static class TestHtmlComponent extends HtmlComponent {
        
        @State String texto = "Incial";
        @State(value = "edad_correcta") int edad = 20;
        @State TestData pojoVal = null;
        @Bind("contador_global") int clics = 0;
        @Prop("usuario_id") Integer userId; 
        @Prop boolean estaMuted = false;
        @Shared(value = "mesa-poker-5") String estadoPartida = "ESPERANDO";

        @Override protected String template() { return "<dummy/>"; }
    }

    // B. POJO DE PRUEBAS
    static class TestData implements java.io.Serializable {
        String id;
        TestData(String id) { this.id = id; }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            return java.util.Objects.equals(id, ((TestData) o).id);
        }
        @Override public int hashCode() { return java.util.Objects.hash(id); }
    }

    private TestHtmlComponent component;

    @BeforeEach
    void setUp() {
        this.component = new TestHtmlComponent();
    }

    @Nested
    @DisplayName("Tests de Reflexión e Inicialización")
    class ReflexionTests {
        @Test
        @DisplayName("Debe construir el mapa de reactividad raw escaneando todas las anotaciones")
        void testRawBindingsReflection() {
            Map<String, ReactiveVar<?>> bindings = component.getRawBindings();

            assertThat(bindings).isNotNull();
            assertThat(bindings).containsKey("texto");
            assertThat(bindings).containsKey("edad_correcta");
            assertThat(bindings).containsKey("contador_global");
            assertThat(bindings).containsKey("usuario_id");
            assertThat(bindings).containsKey("estaMuted");
            assertThat(bindings).containsKey("estadoPartida");
        }
    }

    @Nested
    @DisplayName("Tests de Coerción de Tipos (El Arreglo Clave)")
    class CoercionTests {
        
        @Test
        @SuppressWarnings("unchecked")
        @DisplayName("@Prop: Debe coercer un String entrante del JS a un Integer correcto")
        void testPropCoercion() {
            component._mountRecursive(); // ACTIVAMOS LOS LISTENERS
            ReactiveVar<Object> rxUserId = (ReactiveVar<Object>) component.getRawBindings().get("usuario_id");
            rxUserId.set("100");

            assertThat(component.userId).isEqualTo(100);
        }

        @Test
        @SuppressWarnings("unchecked")
        @DisplayName("@State: Debe coercer un String entrante a un int primitivo")
        void testStateCoercion() {
            component._mountRecursive(); // ACTIVAMOS LOS LISTENERS
            ReactiveVar<Object> rxEdad = (ReactiveVar<Object>) component.getRawBindings().get("edad_correcta");
            rxEdad.set("42");

            assertThat(component.edad).isEqualTo(42);
        }
    }

    @Nested
    @DisplayName("Tests de Snapshots y Deltas (Sincronización Inteligente)")
    class DeltaTests {
        @Test
        @DisplayName("Debe detectar cambios solo en @State y @Bind, ignorando @Prop")
        void testSimpleDeltaDetection() {
            component._captureStateSnapshot();

            component.texto = "Cambiado";
            component.estaMuted = true; // Esto es un @Prop, el servidor no debe enviar delta de esto

            Map<String, Object> deltas = component._getStateDeltas();

            // Solo esperamos 1 cambio ('texto'), porque 'estaMuted' es solo de lectura
            assertThat(deltas).hasSize(1);
            assertThat(deltas).containsEntry("texto", "Cambiado");
            assertThat(deltas).doesNotContainKey("edad_correcta");
        }

        @Test
        @DisplayName("Debe detectar cambios en POJOs complejos usando hashing profundo")
        void testPojoDeltaDetection() {
            component.pojoVal = new TestData("T1");
            component._captureStateSnapshot();

            component.pojoVal = new TestData("T2");

            Map<String, Object> deltas = component._getStateDeltas();

            assertThat(deltas).hasSize(1);
            assertThat(deltas).containsKey("pojoVal");
            assertThat(((TestData) deltas.get("pojoVal")).id).isEqualTo("T2");
        }
    }

    @Nested
    @DisplayName("Tests de IDs y Lifecycle (Mecánica de Vistas)")
    class ViewTests {
        @Test
        @DisplayName("Debe generar IDs únicos y estables")
        void testIdStability() {
            TestHtmlComponent t1 = new TestHtmlComponent();
            TestHtmlComponent t2 = new TestHtmlComponent();
            
            assertThat(t1.getId()).isNotBlank();
            assertThat(t2.getId()).isNotBlank();
            assertThat(t1.getId()).isNotEqualTo(t2.getId());
        }

        @Test
        @SuppressWarnings("unchecked")
        @DisplayName("Cleanup: Al desmontar el componente, los ReactiveVar deben perder sus listeners (memory leaks)")
        void testCleanupOnUnmount() {
            AtomicInteger contadorListeners = new AtomicInteger(0);
            
            ReactiveVar<Object> rxTexto = (ReactiveVar<Object>) component.getRawBindings().get("texto");
            rxTexto.onChange(val -> contadorListeners.incrementAndGet());
            
            component._mountRecursive();
            rxTexto.set("A");
            assertThat(contadorListeners.get()).isEqualTo(1);
            
            component._unmountRecursive();

            rxTexto.set("B");
            assertThat(contadorListeners.get()).isEqualTo(1);
        }
    }
    
    @Nested
    @DisplayName("Tests de Integración con Router (@UrlVariable y @UrlParam)")
    class RouterIntegrationTests {

        static class RouterTestComponent extends HtmlComponent {
            @com.ciro.jreactive.router.UrlVariable("id") 
            int userId;

            @com.ciro.jreactive.router.UrlParam("tab") 
            String activeTab = "inicio";

            @Override protected String template() { return ""; }
        }

        @Test
        @DisplayName("Debe inyectar variables de path (@UrlVariable) con conversión de tipo")
        void testPathVarInjection() {
            RouterTestComponent comp = new RouterTestComponent();
            
            // Simulamos que el Router extrajo el ID de la URL /user/500
            comp._injectParams(Map.of("id", "500"));

            assertThat(comp.userId).isEqualTo(500);
        }

        @Test
        @DisplayName("Debe inyectar Query Params (@UrlParam) correctamente")
        void testQueryParamInjection() {
            RouterTestComponent comp = new RouterTestComponent();

            // Simulamos URL: ?tab=configuracion
            comp._injectQueryParams(Map.of("tab", "configuracion"));

            assertThat(comp.activeTab).isEqualTo("configuracion");
        }

        @Test
        @DisplayName("Debe generar el mapa de bindings de URL para el cliente JS")
        void testUrlBindingsExport() {
            RouterTestComponent comp = new RouterTestComponent();
            Map<String, String> urlMap = comp._getUrlBindings();

            // Esto es vital para que el JS sepa qué variables sincronizar con la URL
            assertThat(urlMap).containsEntry("activeTab", "tab");
        }
    }
    
    @Test
    @DisplayName("Debe permitir forzar la actualización de un campo @State manualmente")
    void testManualUpdateState() {
        component._mountRecursive(); // Encendemos los motores
        
        // 1. Cambiamos el valor directamente (saltándonos la reactividad automática)
        component.texto = "Cambiado a mano";
        
        // 2. Registramos un listener para ver si updateState hace su magia
        java.util.concurrent.atomic.AtomicReference<Object> capturado = new java.util.concurrent.atomic.AtomicReference<>();
        component.getRawBindings().get("texto").onChange(capturado::set);

        // 3. Act: Forzamos la actualización
        component.updateState("texto");

        // 4. Assert: El ReactiveVar interno debió recibir el nuevo valor
        assertThat(capturado.get()).isEqualTo("Cambiado a mano");
    }

    @Test
    @DisplayName("Debe fallar si se intenta actualizar un campo que no existe o no es @State")
    void testUpdateStateInvalidField() {
        // Tu código envuelve el error real en un RuntimeException, así que eso es lo que esperamos
        assertThrows(RuntimeException.class, () -> {
            component.updateState("campo_fantasma");
        });
    }
    
    @Nested
    @DisplayName("Tests de Gestión de Slots y Versiones")
    class SlotsAndVersionsTests {
        @Test
        @DisplayName("Debe manejar correctamente la asignación de slots nombrados")
        void testSlotManagement() {
            component._setSlots(Map.of("header", "<h1>Titulo</h1>", "footer", "<p>Pie</p>"));
            
            assertThat(component._getSlotHtml("header")).isEqualTo("<h1>Titulo</h1>");
            assertThat(component._getSlotHtml("footer")).isEqualTo("<p>Pie</p>");
            assertThat(component._getSlotHtml("inexistente")).isEmpty();
            assertThat(component._getSlotHtml(null)).isEmpty(); // Fallback a default
        }

        @Test
        @DisplayName("Debe gestionar correctamente la versión del componente (Optimistic Locking)")
        void testVersionManagement() {
            component._setVersion(10L);
            assertThat(component._getVersion()).isEqualTo(10L);
        }
    }
    
}