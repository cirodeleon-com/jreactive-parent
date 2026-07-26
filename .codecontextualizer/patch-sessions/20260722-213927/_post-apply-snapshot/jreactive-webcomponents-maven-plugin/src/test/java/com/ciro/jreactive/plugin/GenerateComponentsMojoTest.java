package com.ciro.jreactive.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GenerateComponentsMojo - Pruebas del Generador AOT de Web Components")
class GenerateComponentsMojoTest {

    @Test
    @DisplayName("Debe generar una clase Java válida desde un manifiesto JSON de Web Component")
    void testGenerateSimpleComponent() throws Exception {
        // 1. Simulamos el nodo JSON de un Web Component (ej: extraído de la red)
        String json = """
            {
              "name": "MyButton",
              "customElement": true,
              "members": [
                {
                  "kind": "field",
                  "name": "color",
                  "type": { "text": "string" }
                },
                {
                  "kind": "field",
                  "name": "variant",
                  "type": { "text": "string" }
                }
              ]
            }
        """;
        
        ObjectMapper mapper = new ObjectMapper();
        JsonNode declaration = mapper.readTree(json);

        // 2. Preparamos el directorio temporal
        File outputDir = new File("target/test-generated-sources");
        outputDir.mkdirs();

        // 3. Usamos reflexión para probar el método interno directamente
        GenerateComponentsMojo mojo = new GenerateComponentsMojo();
        Method method = GenerateComponentsMojo.class.getDeclaredMethod("generateJavaClass", String.class, JsonNode.class, File.class, String.class);
        method.setAccessible(true);

        // 4. Ejecutamos la generación
        method.invoke(mojo, new Object[]{"my-button", declaration, outputDir, "com.test.wc"});

        // 5. Verificamos el archivo generado
        File generatedFile = new File(outputDir, "MyButton.java");
        assertThat(generatedFile).exists();
        
        String content = Files.readString(generatedFile.toPath());
        
        assertThat(content).contains("package com.test.wc;");
        assertThat(content).contains("@WebComponent(");
        assertThat(content).contains("tag = \"my-button\"");
        assertThat(content).contains("props = {\"color\", \"variant\"}");
        assertThat(content).contains("public String color;");
    }
}