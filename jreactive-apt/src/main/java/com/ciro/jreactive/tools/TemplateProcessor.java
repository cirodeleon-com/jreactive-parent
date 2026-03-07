package com.ciro.jreactive.tools;

import com.ciro.jreactive.Bind;
import com.ciro.jreactive.State;
import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.annotations.Client;
import com.google.auto.service.AutoService;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.sun.source.tree.*;
import com.ciro.jreactive.ast.ComponentBlueprintCompiler;
import javax.tools.StandardLocation;
import java.io.Reader;
import javax.lang.model.util.Elements;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.DeclaredType;
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
                String rawHtml = extractTemplateString(clazz, tplMethod);

                if (rawHtml != null) {
                    rawHtml = rawHtml.stripIndent().trim();
                    validateTemplateConnections(rawHtml, clazz);
                    generateHtmlResource(clazz, rawHtml);
                    generateClientJs(clazz, rawHtml);
                    generateJavaAccessor(clazz);
                }
            } else if (shouldGenerateAccessor(clazz)) {
                generateJavaAccessor(clazz);
            }
        }
        return false;
    }

    private void validateTemplateConnections(String html, TypeElement clazz) {
        Set<String> validVariables = new HashSet<>(Arrays.asList("this", "id", "true", "false", "null"));
        Set<String> validMethods = new HashSet<>();
        Set<String> validRefs = new HashSet<>();

        for (Element e : getAllMembers(clazz)) {
            if (isValidField(e)) validVariables.add(getBindKey(e));
            if (e.getKind() == ElementKind.METHOD && e.getAnnotation(Call.class) != null) {
                validMethods.add(e.getSimpleName().toString());
            }
        }

        Matcher mEach = Pattern.compile("\\{\\{\\s*#each\\s+[^\\s}]+\\s+as\\s+([\\w]+)\\s*}}").matcher(html);
        while (mEach.find()) validVariables.add(mEach.group(1)); 

        Matcher mExpose = Pattern.compile("expose=[\"']([\\w]+)[\"']").matcher(html);
        while (mExpose.find()) validVariables.add(mExpose.group(1));

        Matcher mRef = Pattern.compile("ref=[\"']([\\w]+)[\"']").matcher(html);
        while (mRef.find()) {
            validRefs.add(mRef.group(1));
            validVariables.add(mRef.group(1));
        }

        Matcher mVar = Pattern.compile("\\{\\{\\s*(?!#|/)(?!else\\b)([\\w.-]+)\\s*}}").matcher(html);
        while (mVar.find()) {
            String rootVar = mVar.group(1).split("\\.")[0];
            if (rootVar.matches("-?\\d+") || rootVar.isEmpty()) continue;
            if (!validVariables.contains(rootVar)) {
                messager.printMessage(Diagnostic.Kind.ERROR, 
                    "❌ [JReactive] La variable '" + rootVar + "' no existe en " + clazz.getSimpleName() + ". Declárala con @State o expose.", clazz);
            }
        }

        Matcher mEvt = Pattern.compile("@\\w+=\"([\\w.-]+)\\s*\\(?").matcher(html);
        while (mEvt.find()) {
            String fullCall = mEvt.group(1); 
            if (fullCall.contains(".")) {
                String refName = fullCall.split("\\.")[0];
                if (!validRefs.contains(refName)) {
                    messager.printMessage(Diagnostic.Kind.ERROR, 
                        "❌ [JReactive] La referencia '" + refName + "' en el método '" + fullCall + "' no existe. Añade ref=\"" + refName + "\".", clazz);
                }
            } else if (!validMethods.contains(fullCall)) {
                messager.printMessage(Diagnostic.Kind.ERROR, 
                    "❌ [JReactive] El método '" + fullCall + "' no existe en " + clazz.getSimpleName() + ". Falta @Call.", clazz);
            }
        }
    }

    private String extractTemplateString(TypeElement clazz, ExecutableElement method) {
        if (trees == null) return null;
        TreePath path = trees.getPath(method);
        if (path == null) return null;
        BlockTree body = ((MethodTree) path.getLeaf()).getBody();
        if (body == null) return null;

        for (StatementTree st : body.getStatements()) {
            if (st instanceof ReturnTree rt && rt.getExpression() instanceof LiteralTree lt && lt.getValue() instanceof String) {
                return (String) lt.getValue();
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
                .findFirst().orElse(null);
    }

    private boolean shouldGenerateAccessor(TypeElement clazz) {
        return isJReactiveComponent(clazz);
    }

    private void generateHtmlResource(TypeElement clazz, String htmlContent) {
        String className = clazz.getSimpleName().toString();
        try {
            FileObject fileObject = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "static/jrx/templates/" + className + ".html");
            try (Writer writer = fileObject.openWriter()) { writer.write(htmlContent); }
        } catch (IOException e) { 
            messager.printMessage(Diagnostic.Kind.ERROR, "Error generando HTML para " + className + ": " + e);
        }
    }

    private void generateClientJs(TypeElement cls, String html) {
        String className = cls.getSimpleName().toString();
        String fileName = "static/js/jrx/" + className + ".jrx.js";
        if (!_generatedClientJs.add(fileName)) return;

        String ownerPackage = processingEnv.getElementUtils()
                .getPackageOf(cls)
                .getQualifiedName()
                .toString();

        String compiledHtml = ComponentBlueprintCompiler.compile(
                html,
                new AptComponentResolver(),
                ownerPackage
        );

        try {
            FileObject resource = filer.createResource(StandardLocation.CLASS_OUTPUT, "", fileName);
            try (Writer writer = resource.openWriter()) {
                writer.write("if(!window.JRX_RENDERERS) window.JRX_RENDERERS = {};\n");
                writer.write("window.JRX_RENDERERS['" + className + "'] = {\n");
                writer.write("  compiled: true,\n");
                writer.write("  getTemplate: function() {\n");
                writer.write("    return `" + compiledHtml
                        .replace("\\", "\\\\")
                        .replace("`", "\\`")
                        .replace("${", "\\${")
                        .replace("\r", "") + "`;\n");
                writer.write("  }\n");
                writer.write("};\n");
            }
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Error generando client JS para " + className + ": " + e.getMessage(),
                    cls);
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
                w.write("    static {\n        AccessorRegistry.register(" + className + ".class, new " + accessorName + "());\n    }\n\n");

                generateWriteMethod(w, clazz, className);
                generateReadMethod(w, clazz, className);
                generateCallMethod(w, clazz, className);

                w.write("}\n");
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    // =========================================================================
    // 🔥 EL MOTOR AOT PROFUNDO (Cero Reflexión)
    // =========================================================================

    private void generateWriteMethod(Writer w, TypeElement clazz, String className) throws IOException {
        w.write("    @Override\n");
        w.write("    public void write(" + className + " t, String p, Object v) {\n");
        w.write("        try {\n");
        w.write("            switch (p) {\n");

        Map<String, String> writes = new LinkedHashMap<>();
        exploreTypesForAccessors(clazz, writes, true);

        for (Map.Entry<String, String> e : writes.entrySet()) {
            w.write("                case \"" + e.getKey() + "\": " + e.getValue() + "; return;\n");
        }

        w.write("            }\n");
        w.write("        } catch (Exception e) {} // Fallback silencioso si un padre es nulo\n");
        w.write("    }\n\n");
    }

    private void generateReadMethod(Writer w, TypeElement clazz, String className) throws IOException {
        w.write("    @Override\n");
        w.write("    public Object read(" + className + " t, String p) {\n");
        w.write("        try {\n");
        w.write("            switch (p) {\n");

        Map<String, String> reads = new LinkedHashMap<>();
        exploreTypesForAccessors(clazz, reads, false);

        for (Map.Entry<String, String> e : reads.entrySet()) {
            w.write("                case \"" + e.getKey() + "\": return " + e.getValue() + ";\n");
        }

        w.write("            }\n");
        w.write("        } catch (NullPointerException e) { return null; } // Optional Chaining ultrarrápido\n");
        w.write("        return null;\n    }\n");
        w.write("    private Object unwrap(Object v) {\n");
        w.write("        if (v instanceof com.ciro.jreactive.ReactiveVar) return ((com.ciro.jreactive.ReactiveVar)v).get();\n");
        w.write("        if (v instanceof com.ciro.jreactive.Type) return ((com.ciro.jreactive.Type)v).get();\n");
        w.write("        return v;\n    }\n\n");
    }

    // 🕸️ Recorredor de Árbol de Tipos (AST)
    private void exploreTypesForAccessors(TypeElement rootClass, Map<String, String> map, boolean isWrite) {
        for (Element e : getAllMembers(rootClass)) {
            if (isValidField(e)) {
                String rootKey = getBindKey(e);
                String rootAccess = "t." + e.getSimpleName().toString();

                if (isWrite) {
                    map.put(rootKey, rootAccess + " = " + castLogic(e.asType(), "v"));
                } else {
                    map.put(rootKey, "unwrap(" + rootAccess + ")");
                }

                TypeMirror innerType = getInnerType(e.asType());
                if (innerType != null && innerType.getKind() == TypeKind.DECLARED) {
                    String castType = getErasure(innerType);
                    TypeElement typeEl = (TypeElement) processingEnv.getTypeUtils().asElement(innerType);
                    exploreDeep(typeEl, rootKey, "((" + castType + ")unwrap(" + rootAccess + "))", map, isWrite, 1, new HashSet<>());
                }
            }
        }
    }

    private void exploreDeep(TypeElement typeEl, String currentPath, String currentAccess, Map<String, String> map, boolean isWrite, int depth, Set<String> visited) {
        if (depth > 3 || typeEl == null) return; // Límite de seguridad
        
        String fqcn = typeEl.getQualifiedName().toString();
        if (fqcn.startsWith("java.")) return; // Ignoramos clases core
        if (!visited.add(fqcn)) return; // Evita bucles infinitos en tipos recursivos

        for (Element e : getAllMembers(typeEl)) {
            if (e.getKind() == ElementKind.FIELD && !e.getModifiers().contains(Modifier.PRIVATE) && !e.getModifiers().contains(Modifier.STATIC)) {
                String fieldName = e.getSimpleName().toString();
                String nextPath = currentPath + "." + fieldName;
                String nextAccess = currentAccess + "." + fieldName;

                if (isWrite) {
                    map.put(nextPath, nextAccess + " = " + castLogic(e.asType(), "v"));
                } else {
                    map.put(nextPath, nextAccess);
                }

                TypeMirror innerType = getInnerType(e.asType());
                if (innerType != null && innerType.getKind() == TypeKind.DECLARED) {
                    String castType = getErasure(innerType);
                    TypeElement nextTypeEl = (TypeElement) processingEnv.getTypeUtils().asElement(innerType);
                    exploreDeep(nextTypeEl, nextPath, "((" + castType + ")" + nextAccess + ")", map, isWrite, depth + 1, new HashSet<>(visited));
                }
            }
        }
    }
    
 // 🔥 NUEVO: Compila el HTML de Mustache a DOM nativo usando tu propio AST
    private String compileToDomStatic(String rawHtml) {
        try {
            // Reutilizamos tu parseador O(N) que ya hace el trabajo pesado
            List<com.ciro.jreactive.ast.JrxNode> nodes = com.ciro.jreactive.ast.JrxParser.parse(rawHtml);
            StringBuilder sb = new StringBuilder();
            for (com.ciro.jreactive.ast.JrxNode n : nodes) {
                // renderRaw ya convierte los IfNode y EachNode en <template data-if="...">!
                n.renderRaw(sb);
            }
            return sb.toString();
        } catch (Exception e) {
            // Fallback seguro en tiempo de compilación
            return rawHtml;
        }
    }

    private TypeMirror getInnerType(TypeMirror type) {
        String tStr = type.toString();
        if (tStr.contains("ReactiveVar") || tStr.contains("Type")) {
            if (type instanceof DeclaredType dt) {
                List<? extends TypeMirror> args = dt.getTypeArguments();
                if (!args.isEmpty()) return args.get(0);
            }
        }
        return type;
    }

    // 🔥 LA SOLUCIÓN: Usar la API del compilador para obtener la ruta absoluta, ignorando anotaciones.
    private String getErasure(TypeMirror type) {
        TypeMirror erased = processingEnv.getTypeUtils().erasure(type);
        
        // Si es un objeto complejo (clase o interfaz) obtenemos su ruta absoluta limpia
        if (erased.getKind() == TypeKind.DECLARED) {
            TypeElement te = (TypeElement) processingEnv.getTypeUtils().asElement(erased);
            if (te != null) {
                return te.getQualifiedName().toString(); // ej: com.ciro.jreactive.SignupPage2.SignupForm
            }
        }
        
        // Fallback seguro para primitivos (int, boolean) y arreglos (byte[])
        String tStr = erased.toString();
        int lastSpace = tStr.lastIndexOf(' ');
        if (lastSpace >= 0) {
            tStr = tStr.substring(lastSpace + 1);
        }
        return tStr;
    }

    // =========================================================================

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
        String t = getErasure(type); // Usamos getErasure que ahora es infalible
        if (type.getKind().isPrimitive()) {
            return switch(type.getKind()) {
                case INT -> "((Number)" + varName + ").intValue()";
                case LONG -> "((Number)" + varName + ").longValue()";
                case BOOLEAN -> "(Boolean)" + varName;
                case DOUBLE -> "((Number)" + varName + ").doubleValue()";
                default -> "(" + t + ")" + varName;
            };
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
    
    
    private final class AptComponentResolver implements ComponentBlueprintCompiler.ComponentResolver {

        @Override
        public ComponentBlueprintCompiler.ResolvedComponent resolve(String tagName, String ownerPackage) {
            String simpleName = simpleName(tagName);
            TypeElement type = locateType(tagName, ownerPackage);

            String packageName = ownerPackage;
            if (type != null) {
                packageName = processingEnv.getElementUtils()
                        .getPackageOf(type)
                        .getQualifiedName()
                        .toString();
            }

            String template = null;

            // 1. Intentar por fuente actual (sirve para componentes del módulo que se está compilando)
            if (type != null) {
                ExecutableElement tplMethod = findTemplateMethod(type);
                if (tplMethod != null) {
                    template = extractTemplateString(type, tplMethod);
                    if (template != null) {
                        template = template.stripIndent().trim();
                    }
                }
            }

            // 2. Fallback por recurso generado (sirve para componentes en dependencias ya compiladas)
            if (template == null || template.isBlank()) {
                template = tryLoadGeneratedTemplate(simpleName);
            }

            if (template == null || template.isBlank()) {
                return null;
            }

            return new ComponentBlueprintCompiler.ResolvedComponent(tagName, packageName, template);
        }
    }
    
    private TypeElement locateType(String tagName, String ownerPackage) {
        String simpleName = simpleName(tagName);
        Elements elements = processingEnv.getElementUtils();

        // 1. Si ya viene calificado
        if (tagName.contains(".")) {
            TypeElement exact = elements.getTypeElement(tagName);
            if (exact != null) return exact;
        }

        // 2. Mismo paquete que el componente padre
        TypeElement local = elements.getTypeElement(ownerPackage + "." + simpleName);
        if (local != null) return local;

        // 3. Paquete base UI (igual que tu runtime)
        TypeElement ui = elements.getTypeElement("com.ciro.jreactive." + simpleName);
        if (ui != null) return ui;

        return null;
    }

    private String simpleName(String tagName) {
        int idx = tagName.lastIndexOf('.');
        return idx >= 0 ? tagName.substring(idx + 1) : tagName;
    }

    private String tryLoadGeneratedTemplate(String simpleName) {
        String path = "static/jrx/templates/" + simpleName + ".html";

        String fromOutput = tryReadResource(StandardLocation.CLASS_OUTPUT, path);
        if (fromOutput != null && !fromOutput.isBlank()) {
            return fromOutput.strip();
        }

        String fromClasspath = tryReadResource(StandardLocation.CLASS_PATH, path);
        if (fromClasspath != null && !fromClasspath.isBlank()) {
            return fromClasspath.strip();
        }

        return null;
    }

    private String tryReadResource(StandardLocation location, String path) {
        try {
            FileObject resource = filer.getResource(location, "", path);
            CharSequence cs = resource.getCharContent(true);
            return cs == null ? null : cs.toString();
        } catch (Exception e) {
            return null;
        }
    }
}