package com.ciro.jreactive.ast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ComponentBlueprintCompiler Unit Tests (El Corazón AOT)")
class ComponentBlueprintCompilerTest {

    // Mockeamos el resolver para que cuando el compilador pregunte por un componente,
    // nosotros le devolvamos un template de mentiras.
    private final ComponentBlueprintCompiler.ComponentResolver mockResolver = (tagName, ownerPackage) -> {
        if ("UserCard".equals(tagName)) {
            return new ComponentBlueprintCompiler.ResolvedComponent(
                "UserCard", 
                "com.app.ui", 
                "<div class='card'>{{name}}</div>"
            );
        }
        return null;
    };

    @Test
    @DisplayName("Debe transformar directivas {{#if}} en etiquetas <template data-if>")
    void testCompileIfDirective() {
        String input = "<div>{{#if activo}}<span>Hola</span>{{/if}}</div>";
        
        // Act
        String result = ComponentBlueprintCompiler.compile(input, mockResolver, "com.app");

        // Assert: El compilador debe convertir el #if en la marca que entiende el JS
        assertThat(result).contains("<template data-if=\"activo\">");
        assertThat(result).contains("<span>Hola</span>");
        assertThat(result).contains("</template>");
    }

    @Test
    @DisplayName("Debe transformar {{#each}} en <template data-each>")
    void testCompileEachDirective() {
        // En tu parser EachNode.java, esperas: {{#each lista as item}}
        String input = "<ul>{{#each items as p}}<li>{{p.name}}</li>{{/each}}</ul>";

        String result = ComponentBlueprintCompiler.compile(input, mockResolver, "com.app");

        // Assert: Verifica la estructura data-each="lista:alias"
        assertThat(result).contains("<template data-each=\"items:p\">");
        assertThat(result).contains("<li>{{p.name}}</li>");
    }

    @Test
    @DisplayName("Debe inyectar templates de componentes hijos (Sub-componentes)")
    void testCompileSubComponent() {
        // Probamos que si escribimos <UserCard />, el compilador traiga su contenido
        String input = "<section><UserCard name='Ciro' /></section>";

        String result = ComponentBlueprintCompiler.compile(input, mockResolver, "com.app");

        // Assert: El resultado debe contener el template del componente hijo resuelto
        assertThat(result).contains("<div class=\"card\">");
        assertThat(result).contains("Ciro"); // El atributo 'name' se inyectó en el {{name}}
    }

    @Test
    @DisplayName("Debe manejar el bloque {{else}} correctamente")
    void testCompileIfElse() {
        String input = "{{#if ok}}SI{{else}}NO{{/if}}";

        String result = ComponentBlueprintCompiler.compile(input, mockResolver, "com.app");

        assertThat(result).contains("<template data-if=\"ok\">SI</template>");
        assertThat(result).contains("<template data-else=\"ok\">NO</template>");
    }

    @Test
    @DisplayName("Debe devolver un string vacío si la entrada es nula o vacía")
    void testCompileEmpty() {
        assertThat(ComponentBlueprintCompiler.compile(null, mockResolver, "p")).isEmpty();
        assertThat(ComponentBlueprintCompiler.compile("   ", mockResolver, "p")).isEmpty();
    }
}