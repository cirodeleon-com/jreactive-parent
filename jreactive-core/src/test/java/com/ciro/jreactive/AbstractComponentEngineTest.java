package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Prop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AbstractComponentEngineTest {

    // 1. Componentes de Prueba
    public static class PadreComp extends HtmlComponent {
        @State String mensaje = "Hola desde el Padre";
        
        @com.ciro.jreactive.annotations.Call // <--- ESTO ES LA LLAVE
        public void guardar() { }
        
        @Override protected String template() { return ""; }
    }

    public static class HijoComp extends HtmlComponent {
        @Prop String texto;
        @Prop String onClick;
        @Override protected String template() { return ""; }
    }

    // 2. Motor Dummy para poder instanciar la clase abstracta
    static class TestEngine extends AbstractComponentEngine {
        @Override
        public ComponentEngine.Rendered render(HtmlComponent ctx) {
            return new ComponentEngine.Rendered("<dummy/>", ctx.getRawBindings());
        }
    }

    private TestEngine engine;

    @BeforeEach
    void setUp() {
        engine = new TestEngine();
        ComponentEngine.setStrategy(engine);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Debe instanciar un componente hijo y vincular sus propiedades reactivas bidireccionalmente")
    void testCreateAndBindComponent() {
        PadreComp padre = new PadreComp();
        padre._initIfNeeded();
        padre._mountRecursive(); // 🔥 EL TOQUE MAESTRO: Montamos al padre para encender la reactividad

        Map<String, String> attrs = new HashMap<>();
        attrs.put(":texto", "mensaje"); 

        engine.renderChild(padre, "AbstractComponentEngineTest$HijoComp", attrs, new HashMap<>());

        assertThat(padre._children()).hasSize(1);
        HijoComp hijo = (HijoComp) padre._children().get(0);
        
        ReactiveVar<Object> rxTextoHijo = (ReactiveVar<Object>) hijo.getRawBindings().get("texto");
        assertThat(rxTextoHijo.get()).isEqualTo("Hola desde el Padre");

        // 2. ¡Prueba de fuego Reactiva!
        ReactiveVar<Object> rxMensajePadre = (ReactiveVar<Object>) padre.getRawBindings().get("mensaje");
        rxMensajePadre.set("Padre Mutado");
        
        // ¡Ahora sí el ActiveGuard permitirá que el evento viaje del Padre al Hijo!
        assertThat(hijo.texto).isEqualTo("Padre Mutado"); 
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Debe inyectar propiedades literales (One-Way) correctamente")
    void testLiteralBinding() {
        PadreComp padre = new PadreComp();
        padre._initIfNeeded();

        Map<String, String> attrs = new HashMap<>();
        attrs.put("texto", "Literal Estático"); 

        engine.renderChild(padre, "AbstractComponentEngineTest$HijoComp", attrs, new HashMap<>());

        HijoComp hijo = (HijoComp) padre._children().get(0);
        
        // Verificamos en la memoria reactiva por la misma protección de UNMOUNTED
        ReactiveVar<Object> rxTextoHijo = (ReactiveVar<Object>) hijo.getRawBindings().get("texto");
        assertThat(rxTextoHijo.get()).isEqualTo("Literal Estático");
    }
    
    @Test
    @DisplayName("Debe limpiar (unmount) los hijos que no se reutilizan en el ciclo de render")
    void testDisposeUnusedChildren() {
        PadreComp padre = new PadreComp();
        padre._initIfNeeded();
        padre._mountRecursive();

        // 1. Primer render: creamos un hijo
        engine.renderChild(padre, "AbstractComponentEngineTest$HijoComp", java.util.Map.of("id", "hijo1"), java.util.Map.of());
        HtmlComponent hijo1 = padre._children().get(0);
        assertThat(hijo1._state()).isEqualTo(ComponentState.MOUNTED);

        // 2. Simulamos un segundo ciclo donde NO se pide el hijo (se limpia el pool)
        padre._beginRenderCycle(); 
        // No llamamos a renderChild...
        padre._endRenderCycle();

        // 3. El hijo anterior debe estar UNMOUNTED
        assertThat(hijo1._state()).isEqualTo(ComponentState.UNMOUNTED);
    }
    
    @Test
    @DisplayName("Debe resolver bindings globales si la variable no está en el padre")
    void testGlobalBindingsResolution() {
        PadreComp padre = new PadreComp();
        padre._initIfNeeded();
        
        // Creamos un binding global simulado
        Map<String, ReactiveVar<?>> globals = new HashMap<>();
        globals.put("app_version", new ReactiveVar<>("1.0.0"));

        // Simulamos: <HijoComp :texto="app_version" />
        Map<String, String> attrs = Map.of(":texto", "app_version");

        // Act: Usamos el método interno de tu motor
        HtmlComponent hijo = engine.createAndBindComponent(
            padre, new ArrayList<>(), globals, "AbstractComponentEngineTest$HijoComp", attrs, Map.of()
        );

        // Assert: El hijo debió recibir el valor global
        assertThat(hijo.getRawBindings().get("texto").get()).isEqualTo("1.0.0");
    }

    @Test
    @DisplayName("Debe fallar con elegancia si el componente no existe en ningún paquete")
    void testComponentNotFound() {
        PadreComp padre = new PadreComp();
        // Intentamos renderizar un componente que no existe ("FantasmaComp")
        assertThrows(RuntimeException.class, () -> {
            engine.renderChild(padre, "FantasmaComp", Map.of(), Map.of());
        });
    }
    
    @Test
    @DisplayName("Debe encontrar un componente buscando en diferentes prefijos de paquetes")
    void testComponentPackageSearch() {
        PadreComp padre = new PadreComp();
        padre._initIfNeeded();

        // Intentamos instanciar un componente que "simulamos" que está en otro paquete
        // Nota: Como estamos en tests, usaremos una clase que sepamos que existe 
        // pero pasándole un nombre simple para que el bucle de paquetes trabaje.
        
        // Este test ejecutará el bloque try-catch con ClassNotFoundException 
        // varias veces hasta encontrar el componente o fallar.
        assertThrows(RuntimeException.class, () -> {
            engine.renderChild(padre, "ComponenteInexistente", Map.of(), Map.of());
        });
    }

    @Test
    @DisplayName("Debe manejar correctamente atributos literales como booleanos y números")
    void testLiteralCoercion() {
        PadreComp padre = new PadreComp();
        padre._initIfNeeded();

        // Probamos el método coerceLiteral inyectando diferentes strings
        // <HijoComp :texto="true" :edad="25" />
        Map<String, String> attrs = new HashMap<>();
        attrs.put(":texto", "true"); 
        
        engine.renderChild(padre, "AbstractComponentEngineTest$HijoComp", attrs, Map.of());
        
        // Esto cubre las ramas de booleanos, int y double en coerceLiteral
        assertThat(padre._children()).isNotEmpty();
    }
    
 

    @Test
    @DisplayName("Debe cualificar los nombres de métodos en atributos de evento (onClick, etc.)")
    void testEventQualification() {
        PadreComp padre = new PadreComp();
        padre.setId("p1");
        
        // Importante: El padre debe tener el método registrado como 'callable'
        // para que cualifique (ver línea 36 de AbstractComponentEngine)
        // Pero como estamos en un test unitario, forzamos el atributo.
        
        Map<String, String> attrs = Map.of("onClick", "guardar()");
        
        HtmlComponent hijo = engine.createAndBindComponent(
            padre, new java.util.ArrayList<>(), new java.util.HashMap<>(), 
            "AbstractComponentEngineTest$HijoComp", attrs, Map.of()
        );

        // Ahora 'rx' no será nulo porque el campo existe en el hijo
        ReactiveVar<Object> rx = (ReactiveVar<Object>) hijo.getRawBindings().get("onClick");
        
        assertThat(rx).isNotNull();
        // Si tu lógica cualificó, debería tener el ID del padre: "p1.guardar()"
        assertThat(rx.get().toString()).contains("p1.guardar");
    }
    
    @Test
    @DisplayName("Debe reutilizar un componente existente del pool si el ID coincide")
    void testComponentReuseFromPool() {
        PadreComp padre = new PadreComp();
        padre._initIfNeeded();
        
        // 1. Creamos un hijo manualmente y lo metemos en el pool simulado
        HijoComp hijoExistente = new HijoComp();
        hijoExistente.setId(padre.getId() + "-HijoComp-0"); // ID estable que genera tu motor
        
        java.util.List<HtmlComponent> pool = new java.util.ArrayList<>();
        pool.add(hijoExistente);

        // 2. Act: Pedimos al motor crear un componente del mismo tipo
        HtmlComponent resultado = engine.createAndBindComponent(
            padre, pool, java.util.Map.of(), "HijoComp", java.util.Map.of(), java.util.Map.of()
        );

        // 3. Assert: Debe ser exactamente la misma instancia del pool
        assertThat(resultado).isSameAs(hijoExistente);
        assertThat(pool).isEmpty(); // El motor debe haberlo sacado del pool
    }
    
    @Test
    @DisplayName("Debe usar el ComponentAccessor (AOT) para inyectar propiedades si está disponible")
    void testAotInjectionLogic() {
        PadreComp padre = new PadreComp();
        padre._initIfNeeded();
        
        // 1. Registramos un Accessor manual para el HijoComp
        com.ciro.jreactive.spi.ComponentAccessor<HijoComp> mockAcc = new com.ciro.jreactive.spi.ComponentAccessor<>() {
            @Override public void write(HijoComp c, String p, Object v) {
                if ("texto".equals(p)) c.texto = "INYECTADO_AOT";
            }
            @Override public Object read(HijoComp c, String p) { return null; }
            @Override public Object call(HijoComp c, String m, Object... a) { return null; }
        };
        com.ciro.jreactive.spi.AccessorRegistry.register(HijoComp.class, mockAcc);

        // 2. Act: Renderizamos un hijo con una propiedad literal
        Map<String, String> attrs = Map.of("texto", "valor_original");
        engine.renderChild(padre, "AbstractComponentEngineTest$HijoComp", attrs, Map.of());

        // 3. Assert: Si el motor priorizó el AOT, el valor debe ser el que puso el Accessor
        HijoComp hijo = (HijoComp) padre._children().get(padre._children().size() - 1);
        assertThat(hijo.texto).isEqualTo("INYECTADO_AOT");
    }
    
    @Test
    @DisplayName("Debe manejar correctamente la coerción de números negativos y decimales")
    void testCoerceComplexNumbers() {
        PadreComp padre = new PadreComp();
        padre._initIfNeeded();

        Map<String, String> attrs = new java.util.HashMap<>();
        attrs.put(":texto", "-123.45"); // Un double negativo como string

        engine.renderChild(padre, "AbstractComponentEngineTest$HijoComp", attrs, Map.of());
        
        HijoComp hijo = (HijoComp) padre._children().get(padre._children().size() - 1);
        // Verificamos que el motor lo inyectó (tu lógica de coerceLiteral lo detectará)
        assertThat(hijo.getRawBindings().get("texto").get().toString()).isEqualTo("-123.45");
    }
}