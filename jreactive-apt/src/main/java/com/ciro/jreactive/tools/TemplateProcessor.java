package com.ciro.jreactive.tools;

import com.ciro.jreactive.Bind;
import com.ciro.jreactive.State;
import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.annotations.Client;
import com.google.auto.service.AutoService;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.sun.source.tree.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@AutoService(Processor.class)
@SupportedAnnotationTypes("*")
public final class TemplateProcessor extends AbstractProcessor {

    private Trees trees;
    private Filer filer;
    private Messager messager;
    private final Set<String> _generatedAccessors = new HashSet<>();
    private final Set<String> _generatedClientJs = new HashSet<>();

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        this.filer = env.getFiler();
        this.messager = env.getMessager();
        try {
            this.trees = Trees.instance(env);
        } catch (Throwable t) {
            // Ignorar en entornos limitados
        }
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        if (round.processingOver()) return false;

        for (Element root : round.getRootElements()) {
            if (root.getKind() != ElementKind.CLASS) continue;
            TypeElement clazz = (TypeElement) root;

            if (clazz.getSimpleName().toString().endsWith("__Accessor")) continue;
            if (!isJReactiveComponent(clazz)) continue;

            ExecutableElement tplMethod = findTemplateMethod(clazz);

            if (tplMethod != null) {
                // 1. Extraer HTML del AST
                String rawHtml = extractTemplateString(clazz, tplMethod);

                if (rawHtml != null) {
                    rawHtml = rawHtml.stripIndent().trim();

                    // 2. Copiloto: Validar plantilla vs Java (Falla el build si hay errores)
                    validateTemplateConnections(rawHtml, clazz);

                    // 3. Generar archivo físico .html (¡Restaurado y sin Jsoup!)
                    generateHtmlResource(clazz, rawHtml);

                    // 4. Generar JS para cliente (@Client)
                    //if (clazz.getAnnotation(Client.class) != null) {
                        generateClientJs(clazz, rawHtml);
                    //}

                    // 5. Generar Accessor Java (O(1) Reflection Bypass)
                    generateJavaAccessor(clazz);
                }
            } else if (shouldGenerateAccessor(clazz)) {
                // Componente sin template pero con estado
                generateJavaAccessor(clazz);
            }
        }
        return false;
    }

 // --- 1. Copiloto: Validación Estricta (El Deber Ser) ---
    private void validateTemplateConnections(String html, TypeElement clazz) {
        // Solo las palabras verdaderamente reservadas del sistema
        Set<String> validVariables = new HashSet<>(Arrays.asList(
            "this", "id", "true", "false", "null"
        ));
        Set<String> validMethods = new HashSet<>();
        Set<String> validRefs = new HashSet<>();

        // 1. Recolectar estado y métodos reales de la clase Java
        for (Element e : getAllMembers(clazz)) {
            if (isValidField(e)) validVariables.add(getBindKey(e));
            if (e.getKind() == ElementKind.METHOD && e.getAnnotation(Call.class) != null) {
                validMethods.add(e.getSimpleName().toString());
            }
        }

        // 2. Aprender alias dinámicos locales (Ej: {{#each lista as alias}})
        Matcher mEach = Pattern.compile("\\{\\{\\s*#each\\s+[^\\s}]+\\s+as\\s+([\\w]+)\\s*}}").matcher(html);
        while (mEach.find()) {
            validVariables.add(mEach.group(1)); 
        }

        // 3. Aprender variables expuestas por componentes hijos (Ej: expose="row")
        Matcher mExpose = Pattern.compile("expose=[\"']([\\w]+)[\"']").matcher(html);
        while (mExpose.find()) {
            validVariables.add(mExpose.group(1)); // El compilador ahora sabe que 'row' es válido aquí
        }

        // 4. Aprender referencias a componentes (Ej: ref="miModal")
        Matcher mRef = Pattern.compile("ref=[\"']([\\w]+)[\"']").matcher(html);
        while (mRef.find()) {
            validRefs.add(mRef.group(1));
            validVariables.add(mRef.group(1)); // Un ref también puede ser leído como variable (Ej: miModal.visible)
        }

        // 5. Validar todas las variables impresas {{ variable.prop }}
        Matcher mVar = Pattern.compile("\\{\\{\\s*(?!#|/)(?!else\\b)([\\w.-]+)\\s*}}").matcher(html);
        while (mVar.find()) {
            String fullPath = mVar.group(1);
            String rootVar = fullPath.split("\\.")[0];
            
            if (rootVar.matches("-?\\d+")) continue; // Ignorar números hardcodeados
            if (rootVar.isEmpty()) continue;

            if (!validVariables.contains(rootVar)) {
                messager.printMessage(Diagnostic.Kind.ERROR, 
                    "❌ [JReactive AST] La variable '" + rootVar + "' no existe en " + clazz.getSimpleName() + ". Declárala con @State/@Bind o expónla en el HTML (expose=\""+rootVar+"\").", clazz);
            }
        }

        // 6. Validar métodos en eventos (@click="metodo()")
        Matcher mEvt = Pattern.compile("@\\w+=\"([\\w.-]+)\\s*\\(?").matcher(html);
        while (mEvt.find()) {
            String fullCall = mEvt.group(1); 
            
            if (fullCall.contains(".")) {
                // Es una llamada a un hijo (Ej: miModal.open)
                String refName = fullCall.split("\\.")[0];
                if (!validRefs.contains(refName)) {
                    messager.printMessage(Diagnostic.Kind.ERROR, 
                        "❌ [JReactive AST] La referencia '" + refName + "' usada en el método '" + fullCall + "' no existe. Añade ref=\"" + refName + "\" al componente HTML.", clazz);
                }
            } else {
                // Es un método local
                if (!validMethods.contains(fullCall)) {
                    messager.printMessage(Diagnostic.Kind.ERROR, 
                        "❌ [JReactive AST] El método '" + fullCall + "' no existe en " + clazz.getSimpleName() + ". Faltó @Call.", clazz);
                }
            }
        }
    }

    // --- Extracción AST ---
    private String extractTemplateString(TypeElement clazz, ExecutableElement method) {
        if (trees == null) return null;
        TreePath path = trees.getPath(method);
        if (path == null) return null;

        MethodTree mt = (MethodTree) path.getLeaf();
        BlockTree body = mt.getBody();
        if (body == null) return null;

        for (StatementTree st : body.getStatements()) {
            if (st instanceof ReturnTree rt) {
                ExpressionTree expr = rt.getExpression();
                if (expr instanceof LiteralTree lt && lt.getValue() instanceof String) {
                    return (String) lt.getValue();
                }
                break;
            }
        }
        return null;
    }

    private boolean isJReactiveComponent(TypeElement clazz) {
        return findTemplateMethod(clazz) != null || 
               clazz.getSuperclass().toString().contains("HtmlComponent") ||
               clazz.getSuperclass().toString().contains("ViewLeaf");
    }

    private ExecutableElement findTemplateMethod(TypeElement clazz) {
        return (ExecutableElement) clazz.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.METHOD)
                .map(e -> (ExecutableElement) e)
                .filter(m -> m.getSimpleName().contentEquals("template") && m.getParameters().isEmpty())
                .findFirst()
                .orElse(null);
    }

    private boolean shouldGenerateAccessor(TypeElement clazz) {
        return isJReactiveComponent(clazz);
    }

    // --- Generadores ---

    private void generateHtmlResource(TypeElement clazz, String htmlContent) {
        String className = clazz.getSimpleName().toString();
        String resourcePath = "static/jrx/templates/" + className + ".html";
        try {
            FileObject fileObject = filer.createResource(StandardLocation.CLASS_OUTPUT, "", resourcePath);
            try (Writer writer = fileObject.openWriter()) {
                writer.write(htmlContent);
            }
        } catch (IOException e) { 
            messager.printMessage(Diagnostic.Kind.ERROR, "Error generando HTML para " + className + ": " + e);
        }
    }

    private void generateClientJs(TypeElement cls, String html) {
        String className = cls.getSimpleName().toString();
        String fileName = "static/js/jrx/" + className + ".jrx.js";
        if (!_generatedClientJs.add(fileName)) return;

        try {
            FileObject resource = filer.createResource(StandardLocation.CLASS_OUTPUT, "", fileName);
            try (Writer writer = resource.openWriter()) {
                writer.write("if(!window.JRX_RENDERERS) window.JRX_RENDERERS = {};\n");
                writer.write("window.JRX_RENDERERS['" + className + "'] = {\n");
                String escapedHtml = html
                        .replace("\\", "\\\\")
                        .replace("`", "\\`")
                        .replace("${", "\\${")
                        .replace("\r", "");
                writer.write("  getTemplate: function() {\n");
                writer.write("    return `" + escapedHtml + "`;\n");
                writer.write("  }\n");
                writer.write("};\n");
            }
        } catch (IOException e) { 
            messager.printMessage(Diagnostic.Kind.ERROR, "Error generando JS: " + e);
        }
    }

    private void generateJavaAccessor(TypeElement clazz) {
        String pkg = processingEnv.getElementUtils().getPackageOf(clazz).getQualifiedName().toString();
        String className = clazz.getSimpleName().toString();
        String accessorName = className + "__Accessor";
        String fqcn = pkg + "." + accessorName;

        if (!_generatedAccessors.add(fqcn)) return;

        try {
            JavaFileObject file = filer.createSourceFile(fqcn, clazz);
            try (Writer w = file.openWriter()) {
                w.write("package " + pkg + ";\n\n");
                w.write("import com.ciro.jreactive.spi.ComponentAccessor;\n");
                w.write("import com.ciro.jreactive.spi.AccessorRegistry;\n");
                w.write("import javax.annotation.processing.Generated;\n\n");

                w.write("@Generated(\"JReactiveAPT\")\n");
                w.write("public class " + accessorName + " implements ComponentAccessor<" + className + "> {\n\n");

                w.write("    static {\n");
                w.write("        AccessorRegistry.register(" + className + ".class, new " + accessorName + "());\n");
                w.write("    }\n\n");

                generateWriteMethod(w, clazz, className);
                generateReadMethod(w, clazz, className);
                generateCallMethod(w, clazz, className);

                w.write("}\n");
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    // --- Métodos de Desreflexión (O(1)) ---
    private void generateWriteMethod(Writer w, TypeElement clazz, String className) throws IOException {
        w.write("    @Override\n");
        w.write("    public void write(" + className + " t, String p, Object v) {\n");
        w.write("        switch (p) {\n");
        for (Element e : getAllMembers(clazz)) {
            if (isValidField(e)) {
                String name = e.getSimpleName().toString();
                String key = getBindKey(e);
                w.write("            case \"" + key + "\": t." + name + " = " + castLogic(e.asType(), "v") + "; break;\n");
            }
        }
        w.write("        }\n    }\n\n");
    }

    private void generateReadMethod(Writer w, TypeElement clazz, String className) throws IOException {
        w.write("    @Override\n");
        w.write("    public Object read(" + className + " t, String p) {\n");
        w.write("        switch (p) {\n");
        for (Element e : getAllMembers(clazz)) {
            if (isValidField(e)) {
                String name = e.getSimpleName().toString();
                String key = getBindKey(e);
                w.write("            case \"" + key + "\": return unwrap(t." + name + ");\n");
            }
        }
        w.write("            default: return null;\n        }\n    }\n");
        w.write("    private Object unwrap(Object v) {\n");
        w.write("        if (v instanceof com.ciro.jreactive.ReactiveVar) return ((com.ciro.jreactive.ReactiveVar)v).get();\n");
        w.write("        if (v instanceof com.ciro.jreactive.Type) return ((com.ciro.jreactive.Type)v).get();\n");
        w.write("        return v;\n    }\n\n");
    }

    private void generateCallMethod(Writer w, TypeElement clazz, String className) throws IOException {
        w.write("    @Override\n");
        w.write("    public Object call(" + className + " t, String m, Object... args) {\n");
        w.write("        switch (m) {\n");
        for (Element e : getAllMembers(clazz)) {
            if (e.getKind() == ElementKind.METHOD && e.getAnnotation(Call.class) != null) {
                ExecutableElement method = (ExecutableElement) e;
                String name = method.getSimpleName().toString();
                w.write("            case \"" + name + "\":\n");
                w.write("                t." + name + "(");
                List<? extends VariableElement> params = method.getParameters();
                for (int i = 0; i < params.size(); i++) {
                    w.write(castLogic(params.get(i).asType(), "args[" + i + "]"));
                    if (i < params.size() - 1) w.write(", ");
                }
                w.write(");\n");
                w.write("                return null;\n");
            }
        }
        w.write("            default: throw new IllegalArgumentException(\"Method not found: \" + m);\n");
        w.write("        }\n    }\n\n");
    }

    private String castLogic(TypeMirror type, String varName) {
        String t = type.toString();
        if (t.contains("<")) t = t.substring(0, t.indexOf("<")); // Type Erasure
        
        if (type.getKind().isPrimitive()) {
            switch(type.getKind()) {
                case INT: return "((Number)" + varName + ").intValue()";
                case LONG: return "((Number)" + varName + ").longValue()";
                case BOOLEAN: return "(Boolean)" + varName;
                case DOUBLE: return "((Number)" + varName + ").doubleValue()";
                default: return "(" + t + ")" + varName;
            }
        }
        return "(" + t + ")" + varName;
    }

    private boolean isValidField(Element e) {
        return e.getKind() == ElementKind.FIELD && 
               !e.getModifiers().contains(Modifier.PRIVATE) && 
               !e.getModifiers().contains(Modifier.STATIC) &&
               (e.getAnnotation(State.class) != null || e.getAnnotation(Bind.class) != null);
    }

    private String getBindKey(Element e) {
        State s = e.getAnnotation(State.class);
        Bind b = e.getAnnotation(Bind.class);
        String name = e.getSimpleName().toString();
        if (s != null && !s.value().isBlank()) return s.value();
        if (b != null && !b.value().isBlank()) return b.value();
        return name;
    }

    private List<Element> getAllMembers(TypeElement te) {
        List<Element> elements = new ArrayList<>();
        TypeElement current = te;
        while (current != null && !current.getQualifiedName().toString().equals("java.lang.Object")) {
            elements.addAll(current.getEnclosedElements());
            TypeMirror superclass = current.getSuperclass();
            if (superclass.getKind() == TypeKind.NONE) break;
            current = (TypeElement) processingEnv.getTypeUtils().asElement(superclass);
        }
        return elements;
    }
}