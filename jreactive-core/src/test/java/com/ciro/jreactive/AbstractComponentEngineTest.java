package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Prop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractComponentEngineTest {

    // 1. Componentes de Prueba
    public static class PadreComp extends HtmlComponent {
        @State String mensaje = "Hola desde el Padre";
        @Override protected String template() { return ""; }
    }

    public static class HijoComp extends HtmlComponent {
        @Prop String texto;
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
}