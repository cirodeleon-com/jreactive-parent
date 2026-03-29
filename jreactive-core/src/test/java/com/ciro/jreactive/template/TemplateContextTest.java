package com.ciro.jreactive.template;

import com.ciro.jreactive.HtmlComponent;
import com.ciro.jreactive.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TemplateContext Unit Tests (El Cerebro de JReactive)")
class TemplateContextTest {

    // 1. POJO / Record para probar resolución anidada (Ej: user.nombre)
    public record Usuario(String nombre, int edad) {}

    // 2. Componente Dummy con un estado bien variado
    static class TestComponent extends HtmlComponent {
        @State String titulo = "Dashboard";
        @State boolean activo = true;
        @State int contador = 5;
        @State Usuario user = new Usuario("Ciro", 30);
        @State List<String> tareas = List.of("A", "B", "C");
        @State List<String> vacia = List.of();

        @Override
        protected String template() { return "<div></div>"; }
    }

    private TestComponent component;
    private TemplateContext ctx;

    @BeforeEach
    void setUp() {
        component = new TestComponent();
        // Forzamos el escaneo de anotaciones inicial (esto simula el ciclo de vida real)
        component.getRawBindings(); 
        ctx = new TemplateContext(component);
    }

    @Test
    @DisplayName("Debe resolver literales directamente (Strings, booleanos, números)")
    void testResolveLiterals() {
        assertThat(ctx.resolve("'hola'")).isEqualTo("hola");
        assertThat(ctx.resolve("true")).isEqualTo(true);
        assertThat(ctx.resolve("false")).isEqualTo(false);
        assertThat(ctx.resolve("42")).isEqualTo(42.0); // Lo parsea como double internamente
    }

    @Test
    @DisplayName("Debe resolver variables de estado simples del componente")
    void testResolveSimpleState() {
        assertThat(ctx.resolve("titulo")).isEqualTo("Dashboard");
        assertThat(ctx.resolve("activo")).isEqualTo(true);
        assertThat(ctx.resolve("contador")).isEqualTo(5);
    }

    @Test
    @DisplayName("Debe navegar y resolver propiedades anidadas usando Reflexión")
    void testResolveNestedProperties() {
        // El framework tiene que saber llamar a user.nombre() internamente
        assertThat(ctx.resolve("user.nombre")).isEqualTo("Ciro");
        assertThat(ctx.resolve("user.edad")).isEqualTo(30);
    }

    @Test
    @DisplayName("Debe devolver null silenciosamente si la ruta no existe (Evita explotar la vista)")
    void testResolveNullSate() {
        assertThat(ctx.resolve("no_existe")).isNull();
        assertThat(ctx.resolve("user.apellido")).isNull();
        assertThat(ctx.resolve(null)).isNull();
        assertThat(ctx.resolve("")).isNull();
    }

    @Test
    @DisplayName("Debe calcular el tamaño de Listas y Strings dinámicamente (.size / .length)")
    void testResolveSizeAndLength() {
        assertThat(ctx.resolve("tareas.size")).isEqualTo(3);
        assertThat(ctx.resolve("titulo.length")).isEqualTo(9); // "Dashboard".length()
    }

    @Test
    @DisplayName("Debe evaluar expresiones booleanas y lógica Truthy/Falsy")
    void testEvaluateTruthyFalsy() {
        // Booleanos puros
        assertThat(ctx.evaluate("activo")).isTrue();
        assertThat(ctx.evaluate("!activo")).isFalse();

        // Colecciones (llena = true, vacía = false)
        assertThat(ctx.evaluate("tareas")).isTrue();
        assertThat(ctx.evaluate("!tareas")).isFalse();
        
        assertThat(ctx.evaluate("vacia")).isFalse();
        assertThat(ctx.evaluate("!vacia")).isTrue();

        // Números (0 = false, >0 = true)
        assertThat(ctx.evaluate("contador")).isTrue();
        
        // Strings y Nulos (lleno = true, vacío/null = false)
        assertThat(ctx.evaluate("titulo")).isTrue();
        assertThat(ctx.evaluate("no_existe")).isFalse();
        assertThat(ctx.evaluate("!no_existe")).isTrue(); // La negación de null es true
    }

    @Test
    @DisplayName("Debe aislar variables locales usando createChild() (Soporte para bucles #each)")
    void testLocalVariablesInChildContext() {
        // Simulamos que estamos dentro de un bucle: {{#each u in usuarios}}
        TemplateContext childCtx = ctx.createChild(Map.of("u", new Usuario("Maria", 25)));

        // 1. Debe resolver la variable local inyectada
        assertThat(childCtx.resolve("u.nombre")).isEqualTo("Maria");
        assertThat(childCtx.resolve("u.edad")).isEqualTo(25);

        // 2. Debe seguir resolviendo el estado global del padre (Scope fallback)
        assertThat(childCtx.resolve("titulo")).isEqualTo("Dashboard");
        
        // 3. El padre NO debe contaminarse con las variables de los hijos
        assertThat(ctx.resolve("u.nombre")).isNull();
    }
}