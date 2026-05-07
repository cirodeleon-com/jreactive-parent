package com.ciro.jreactive.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.*;

@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GenerateComponentsMojo extends AbstractMojo {

    @Parameter
    private List<LibraryConfig> libraries;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    // Diccionario de sanitización para palabras reservadas de Java
    private static final Map<String, String> RESERVED_MAP = Map.of(
        "class", "cssClass",
        "for", "htmlFor",
        "default", "defaultValue",
        "switch", "toggleSwitch",
        "type", "componentType"
    );

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void execute() throws MojoExecutionException {
        if (libraries == null || libraries.isEmpty()) {
            getLog().warn("⚠️ [JReactive] No se configuraron librerías en el plugin.");
            return;
        }

        getLog().info("🚀 [JReactive] Ejecutando Auto-Generador Multi-Librería...");

        String baseOutputDir = project.getBuild().getDirectory() + "/generated-sources/jreactive";
        project.addCompileSourceRoot(baseOutputDir);

        for (LibraryConfig lib : libraries) {
            processLibrary(lib, baseOutputDir);
        }
    }

    private void processLibrary(LibraryConfig lib, String baseOutputDir) {
        getLog().info("📥 Procesando manifiesto: " + lib.getJsonUrl());
        
        File outputDir = new File(baseOutputDir, lib.getTargetPackage().replace('.', '/'));
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            getLog().error("❌ [JReactive] No se pudo crear el directorio: " + outputDir);
            return; 
        }

        try {
            java.net.URLConnection conn = URI.create(lib.getJsonUrl()).toURL().openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 JReactive/1.0");
            
            try (InputStream in = conn.getInputStream()) {
                JsonNode rootNode = mapper.readTree(in);
                int count = 0;
                int errors = 0;

                JsonNode modules = rootNode.path("modules");
                if (modules.isArray()) {
                    for (JsonNode module : modules) {
                        for (JsonNode decl : module.path("declarations")) {
                            if (decl.path("customElement").asBoolean()) {
                                
                                // Intentamos leer el tag explícito
                                String tagName = decl.path("tagName").asText(null);
                                
                                // 🧠 MAGIA DE INFERENCIA: Si la librería omitió el tagName, lo inferimos del nombre de la clase
                                if (tagName == null || tagName.isBlank() || tagName.equals("null")) {
                                    String rawClassName = decl.path("name").asText(null);
                                    if (rawClassName != null && !rawClassName.isBlank()) {
                                        tagName = pascalToKebab(rawClassName);
                                    } else {
                                        continue; // Si tampoco hay nombre de clase, es un fantasma real. Ignorar.
                                    }
                                }

                                try {
                                    generateJavaClass(tagName, decl, outputDir, lib.getTargetPackage());
                                    count++;
                                } catch (Exception ex) {
                                    errors++;
                                    getLog().debug("⚠️ Saltando componente rebelde [" + tagName + "]: " + ex.getMessage());
                                }
                            }
                        }
                    }
                }
                
                if (errors > 0) {
                    getLog().warn("⚠️ [JReactive] " + count + " generados, " + errors + " omitidos en " + lib.getTargetPackage());
                } else {
                    getLog().info("✅ [JReactive] " + count + " componentes generados en " + lib.getTargetPackage());
                }
            }
        } catch (java.io.FileNotFoundException e) {
            getLog().error("❌ [JReactive] ERROR 404: El manifiesto no existe en " + lib.getJsonUrl());
        } catch (Exception e) {
            getLog().error("❌ [JReactive] Fallo de red o formato en " + lib.getJsonUrl() + " -> " + e.getMessage());
        }
    }

    private void generateJavaClass(String tagName, JsonNode declaration, File outputDir, String targetPackage) throws Exception {
        String className = kebabToPascal(tagName);
        StringBuilder sb = new StringBuilder();

        List<String> propNames = new ArrayList<>();
        List<String> eventNames = new ArrayList<>();

        for (JsonNode member : declaration.path("members")) {
            if ("field".equals(member.path("kind").asText()) && 
                ("public".equals(member.path("privacy").asText()) || member.path("privacy").isMissingNode())) {
                
                String rawName = member.path("name").asText();
                if (rawName == null || !rawName.matches("^[a-zA-Z_$][a-zA-Z0-9_$]*$")) {
                    continue; 
                }
                propNames.add(rawName);
            }
        }

        for (JsonNode ev : declaration.path("events")) {
            String evName = ev.path("name").asText();
            if (evName != null && !evName.isBlank() && !evName.contains(":") && !evName.contains("[")) {
                eventNames.add(evName);
            }
        }

        sb.append("package ").append(targetPackage).append(";\n\n");
        sb.append("import com.ciro.jreactive.HtmlComponent;\n");
        sb.append("import com.ciro.jreactive.annotations.Prop;\n");
        sb.append("import com.ciro.jreactive.annotations.WebComponent;\n\n");
        sb.append("import javax.annotation.processing.Generated;\n\n");
        
        sb.append("@Generated(\"JReactive WebComponent Plugin\")\n");
        sb.append("@WebComponent(\n");
        sb.append("    tag = \"").append(tagName).append("\"");
        
        if (!propNames.isEmpty()) {
            sb.append(",\n    props = {");
            sb.append(propNames.stream().map(p -> "\"" + p + "\"").reduce((a, b) -> a + ", " + b).orElse(""));
            sb.append("}");
        }
        
        if (!eventNames.isEmpty()) {
            sb.append(",\n    events = {");
            sb.append(eventNames.stream().map(e -> "\"" + e + "\"").reduce((a, b) -> a + ", " + b).orElse(""));
            sb.append("}");
        }
        sb.append("\n)\n");
        
        sb.append("public class ").append(className).append(" extends HtmlComponent {\n\n");

        for (String rawName : propNames) {
            String typeStr = "String";
            for (JsonNode member : declaration.path("members")) {
                if (rawName.equals(member.path("name").asText())) {
                    typeStr = member.path("type").path("text").asText("String").toLowerCase();
                    break;
                }
            }

            String javaName = RESERVED_MAP.getOrDefault(rawName, rawName);
            String javaType = "String";
            
            if (typeStr.contains("boolean")) javaType = "boolean";
            else if (typeStr.contains("number")) javaType = "double";

            if (!javaName.equals(rawName)) {
                sb.append("    @Prop(\"").append(rawName).append("\") ");
            } else {
                sb.append("    @Prop ");
            }
            sb.append("public ").append(javaType).append(" ").append(javaName).append(";\n");
        }

        sb.append("\n");
        for (String evName : eventNames) {
            sb.append("    @Prop public String on")
              .append(kebabToPascal(evName))
              .append(" = \"\";\n"); // <-- Se añade la inicialización vacía
        }

        sb.append("}\n");

        Files.writeString(new File(outputDir, className + ".java").toPath(), sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private String kebabToPascal(String kebab) {
        return Arrays.stream(kebab.split("-"))
            .map(p -> {
                if (p.isEmpty()) return "";
                return p.substring(0, 1).toUpperCase() + p.substring(1);
            })
            .reduce("", String::concat);
    }

    // 🧠 INFERENCIA: Convierte "MdOutlinedButton" a "md-outlined-button"
    private String pascalToKebab(String pascal) {
        if (pascal == null || pascal.isEmpty()) return "";
        return pascal.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase();
    }
}