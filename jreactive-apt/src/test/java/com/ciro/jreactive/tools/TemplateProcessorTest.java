package com.ciro.jreactive.tools;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TemplateProcessor - Pruebas del Compilador AOT")
class TemplateProcessorTest {

    @Test
    @DisplayName("Debe compilar un @WebComponent y generar su clase __Accessor con Reflection-free code")
    void testWebComponentCompilation() throws Exception {
        
        // Simulamos un archivo .java creado por un developer en tu framework
        var componentSource = JavaFileObjects.forSourceString(
            "com.test.MiBoton",
            """
            package com.test;
            import com.ciro.jreactive.HtmlComponent;
            import com.ciro.jreactive.annotations.WebComponent;
            import com.ciro.jreactive.annotations.Prop;

            @WebComponent(tag = "mi-boton", props = {"color"})
            public class MiBoton extends HtmlComponent {
                @Prop public String color = "rojo";
                
                @Override
                protected String template() { return ""; }
            }
            """
        );

        // Disparamos el compilador de Java pasándole tu Annotation Processor
        Compilation compilation = javac()
            .withProcessors(new TemplateProcessor())
            .compile(componentSource);

        // 1. Debe compilar exitosamente
        assertThat(compilation.status()).isEqualTo(Compilation.Status.SUCCESS);

        // 2. Debe haber generado el archivo MiBoton__Accessor.java
        assertThat(compilation.generatedSourceFile("com.test.MiBoton__Accessor").isPresent()).isTrue();
        
        // 3. Revisamos el código generado por dentro (El corazón AOT)
        String accessorContent = compilation.generatedSourceFile("com.test.MiBoton__Accessor").get().getCharContent(false).toString();
        
        assertThat(accessorContent).contains("public class MiBoton__Accessor implements ComponentAccessor<MiBoton>");
        // 🔥 EL FIX: Esperamos que use 'unwrap' en la lectura y haga el cast en la escritura
        assertThat(accessorContent).contains("case \"color\": return unwrap(t.color);");
        assertThat(accessorContent).contains("case \"color\": t.color = (java.lang.String)v;");
    }
    
    @Test
    @DisplayName("Debe hacer fallar la compilación si un componente no tiene su método template() ni archivo .html")
    void testMissingTemplateFailsCompilation() {
        var componentSource = JavaFileObjects.forSourceString(
            "com.test.ComponenteMalo",
            """
            package com.test;
            import com.ciro.jreactive.HtmlComponent;

            public class ComponenteMalo extends HtmlComponent {
                // El desarrollador se olvidó del método template()
            }
            """
        );

        Compilation compilation = javac()
            .withProcessors(new TemplateProcessor())
            .compile(componentSource);

        // 1. La compilación DEBE fracasar (tu Verdad Funcional detiene el build)
        assertThat(compilation.status()).isEqualTo(Compilation.Status.FAILURE);
        
        // 2. Revisamos que tu error personalizado haya salido en la consola del compilador
        assertThat(compilation.errors().toString()).contains("ERROR FATAL: No se encontró el template HTML para el componente 'ComponenteMalo'");
    }
    
    @Test
    @DisplayName("Debe compilar un HtmlComponent estándar, validar su template y generar el AST y Client JS")
    void testStandardComponentCompilation() throws Exception {
        var componentSource = JavaFileObjects.forSourceString(
            "com.test.MiPagina",
            """
            package com.test;
            import com.ciro.jreactive.HtmlComponent;
            import com.ciro.jreactive.State;
            import com.ciro.jreactive.annotations.Call;

            public class MiPagina extends HtmlComponent {
                @State public String nombre = "Mundo";
                
                @Call public void saludar() {}
                
                @Override
                protected String template() { 
                    return "<div><h1>Hola {{nombre}}</h1><button @click=\\"saludar()\\">Boton</button></div>"; 
                }
            }
            """
        );

        Compilation compilation = javac()
            .withProcessors(new TemplateProcessor())
            .compile(componentSource);

        assertThat(compilation.status()).isEqualTo(Compilation.Status.SUCCESS);
        
        // 1. Verificamos la generación del Accessor (AST y Reflection-free)
        String accessorContent = compilation.generatedSourceFile("com.test.MiPagina__Accessor").get().getCharContent(false).toString();
        assertThat(accessorContent).contains("case \"nombre\":");
        assertThat(accessorContent).contains("case \"saludar\":");
        assertThat(accessorContent).contains("new com.ciro.jreactive.ast.ElementNode(\"div\", false)"); 
        
        // 2. Verificamos la generación del archivo JS para el cliente
        boolean jsGenerated = compilation.generatedFile(javax.tools.StandardLocation.CLASS_OUTPUT, "static/js/jrx/com_test_MiPagina.jrx.js").isPresent();
        assertThat(jsGenerated).isTrue();
    }

    @Test
    @DisplayName("Debe fallar la compilación si el template hace referencia a variables o métodos inexistentes")
    void testTemplateValidationFailures() {
        var componentSource = JavaFileObjects.forSourceString(
            "com.test.PaginaRota",
            """
            package com.test;
            import com.ciro.jreactive.HtmlComponent;

            public class PaginaRota extends HtmlComponent {
                @Override
                protected String template() { 
                    // Usamos una variable fantasma y un método fantasma
                    return "<div @click=\\"metodoFantasma()\\">{{variableFantasma}}</div>"; 
                }
            }
            """
        );

        Compilation compilation = javac()
            .withProcessors(new TemplateProcessor())
            .compile(componentSource);

        // 1. La compilación debe fallar (tu validador de conexiones funciona)
        assertThat(compilation.status()).isEqualTo(Compilation.Status.FAILURE);
        
        // 2. Verificamos que imprima exactamente los errores que programaste
        String errors = compilation.errors().toString();
        assertThat(errors).contains("La variable 'variableFantasma' no existe");
        assertThat(errors).contains("El método 'metodoFantasma' no existe");
    }

    @Test
    @DisplayName("Debe resolver subcomponentes usando el AptComponentResolver para compilar el JS del cliente")
    void testSubComponentResolution() throws Exception {
        // Simulamos un componente Hijo
        var childSource = JavaFileObjects.forSourceString(
            "com.test.Hijo",
            """
            package com.test;
            import com.ciro.jreactive.HtmlComponent;
            public class Hijo extends HtmlComponent {
                @Override protected String template() { return "<span class=\\"hijo\\">Soy hijo</span>"; }
            }
            """
        );
        
        // Simulamos un componente Padre que usa al Hijo
        var parentSource = JavaFileObjects.forSourceString(
            "com.test.Padre",
            """
            package com.test;
            import com.ciro.jreactive.HtmlComponent;
            import com.ciro.jreactive.annotations.Client;
            @Client
            public class Padre extends HtmlComponent {
                @Override protected String template() { return "<div><Hijo /></div>"; }
            }
            """
        );

        // Compilamos ambos a la vez
        Compilation compilation = javac()
            .withProcessors(new TemplateProcessor())
            .compile(childSource, parentSource);

        assertThat(compilation.status()).isEqualTo(Compilation.Status.SUCCESS);
        
        // Verificamos que el JS generado para el Padre haya inyectado (compilado estáticamente) el template del Hijo
        String jsContent = compilation.generatedFile(javax.tools.StandardLocation.CLASS_OUTPUT, "static/js/jrx/com_test_Padre.jrx.js").get().getCharContent(false).toString();
        
        // El AptComponentResolver tuvo que haber hecho su magia
        assertThat(jsContent).contains("Soy hijo"); 
    }
    
    @Test
    @DisplayName("Debe generar Accessor pero ignorar la falta de template() si la clase es abstracta")
    void testAbstractClassIgnoresTemplate() {
        var abstractSource = JavaFileObjects.forSourceString(
            "com.test.PaginaBase",
            """
            package com.test;
            import com.ciro.jreactive.HtmlComponent;
            import com.ciro.jreactive.State;

            // Al ser abstracta, el procesador no debe exigir que tenga HTML
            public abstract class PaginaBase extends HtmlComponent {
                @State public String tituloGlobal = "JReactive";
            }
            """
        );

        com.google.testing.compile.Compilation compilation = com.google.testing.compile.Compiler.javac()
            .withProcessors(new TemplateProcessor())
            .compile(abstractSource);

        // 1. Debe compilar exitosamente (sin lanzar el ERROR FATAL de template faltante)
        assertThat(compilation.status()).isEqualTo(com.google.testing.compile.Compilation.Status.SUCCESS);
        
        // 2. Debe generar el Accessor para que los hijos hereden las propiedades por AOT
        assertThat(compilation.generatedSourceFile("com.test.PaginaBase__Accessor").isPresent()).isTrue();
    }
    
    @Test
    @DisplayName("Debe explorar POJOs profundamente y hacer cast de primitivos/wrappers para el Accessor")
    void testDeepPojoAndPrimitivesInAccessor() throws Exception {
        var componentSource = com.google.testing.compile.JavaFileObjects.forSourceString(
            "com.test.ComplexComp",
            """
            package com.test;
            import com.ciro.jreactive.HtmlComponent;
            import com.ciro.jreactive.State;
            import com.ciro.jreactive.Bind;
            import com.ciro.jreactive.Type;
            import com.ciro.jreactive.ReactiveVar;

            public class ComplexComp extends HtmlComponent {
                // POJO profundo
                @State public User user = new User();
                
                // Primitivos complejos
                @State public boolean active;
                @State public Long count;
                @State public Double price;
                
                // Wrappers de JReactive
                @Bind public Type<String> titulo = Type.of("hola");
                @Bind public ReactiveVar<Integer> clics = new ReactiveVar<>(0);
                
                public static class User {
                    public String name;
                    public Address address = new Address();
                }
                public static class Address {
                    public String street;
                }
                
                @Override protected String template() { 
                    return "<div>{{user.address.street}} {{titulo}}</div>"; 
                }
            }
            """
        );

        com.google.testing.compile.Compilation compilation = com.google.testing.compile.Compiler.javac()
            .withProcessors(new TemplateProcessor())
            .compile(componentSource);

        assertThat(compilation.status()).isEqualTo(com.google.testing.compile.Compilation.Status.SUCCESS);
        String accessorContent = compilation.generatedSourceFile("com.test.ComplexComp__Accessor").get().getCharContent(false).toString();
        
        // 1. Verificamos que exploró el POJO profundamente
        assertThat(accessorContent).contains("case \"user.address.street\":");
        
        // 2. Verificamos el casteo inteligente de primitivos
     // 2. Verificamos el casteo inteligente de primitivos (Soporta JSON strings)
        assertThat(accessorContent).contains("(v instanceof String ? Boolean.valueOf((String)v) : (Boolean)v)");
        assertThat(accessorContent).contains("(v instanceof String ? Long.valueOf((String)v) : ((Number)v).longValue())");
        assertThat(accessorContent).contains("(java.lang.Double)v");
        
        // 3. Verificamos que usa .set() seguro para los Type y ReactiveVar en lugar de igualación directa
        assertThat(accessorContent).contains("if (t.titulo != null) { t.titulo.set((java.lang.String)v); }");
        assertThat(accessorContent).contains("if (t.clics != null) { t.clics.set(");
    }

    @Test
    @DisplayName("Debe validar referencias a componentes hijos (@click='ref.metodo()') y fallar si no existe el ref")
    void testRefValidation() {
        var componentSource = com.google.testing.compile.JavaFileObjects.forSourceString(
            "com.test.RefMalaComp",
            """
            package com.test;
            import com.ciro.jreactive.HtmlComponent;

            public class RefMalaComp extends HtmlComponent {
                @Override protected String template() { 
                    // Se usa refFalsa.metodo() pero refFalsa no está declarada con ref="refFalsa" en el HTML
                    return "<button @click=\\"refFalsa.saludar()\\">Click</button>"; 
                }
            }
            """
        );

        com.google.testing.compile.Compilation compilation = com.google.testing.compile.Compiler.javac()
            .withProcessors(new TemplateProcessor())
            .compile(componentSource);

        // La compilación DEBE fallar
        assertThat(compilation.status()).isEqualTo(com.google.testing.compile.Compilation.Status.FAILURE);
        // El validador debe imprimir la alerta exacta
        assertThat(compilation.errors().toString()).contains("La referencia 'refFalsa' en el método 'refFalsa.saludar' no existe");
    }

    @Test
    @DisplayName("Debe compilar HTML usando las variables #each y expose correctamente sin lanzar error de variable faltante")
    void testHtmlWithEachAndExpose() {
        var componentSource = com.google.testing.compile.JavaFileObjects.forSourceString(
            "com.test.EachComp",
            """
            package com.test;
            import com.ciro.jreactive.HtmlComponent;
            import com.ciro.jreactive.State;

            public class EachComp extends HtmlComponent {
                @State public java.util.List<String> items;
                
                @Override protected String template() { 
                    // 'item' viene del as, 'miTabla' viene de expose, 'miRef' viene de ref
                    return "<div>" +
                           "  {{#each items as item}} <span>{{item}}</span> {{/each}}" +
                           "  <JTable expose=\\"miTabla\\">{{miTabla.id}}</JTable>" +
                           "  <div ref=\\"miRef\\">{{miRef}}</div>" +
                           "</div>"; 
                }
            }
            """
        );

        com.google.testing.compile.Compilation compilation = com.google.testing.compile.Compiler.javac()
            .withProcessors(new TemplateProcessor())
            .compile(componentSource);

        // La compilación debe ser exitosa porque el Validador de JReactive debe
        // reconocer que 'item', 'miTabla' y 'miRef' son variables válidas inyectadas por el HTML
        assertThat(compilation.status()).isEqualTo(com.google.testing.compile.Compilation.Status.SUCCESS);
    }
}