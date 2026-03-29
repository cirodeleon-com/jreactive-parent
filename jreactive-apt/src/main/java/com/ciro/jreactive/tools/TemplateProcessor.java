package com.ciro.jreactive.tools;

import com.ciro.jreactive.Bind;
import com.ciro.jreactive.State;
import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.annotations.Client;
import com.ciro.jreactive.annotations.Prop;
import com.ciro.jreactive.annotations.Shared;
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

            String rawHtml = tryReadHtmlFile(clazz);
            String rawCss = tryReadCssFile(clazz);
            ExecutableElement tplMethod = findTemplateMethod(clazz);
            
            // 🔥 CIRUGÍA: Interceptar @WebComponent antes del flujo normal
            com.ciro.jreactive.annotations.WebComponent wcAnn = clazz.getAnnotation(com.ciro.jreactive.annotations.WebComponent.class);
            if (wcAnn != null) {
                rawHtml = generateWebComponentHtml(wcAnn);
                generateHtmlResource(clazz, rawHtml);
                generateClientJs(clazz, rawHtml);
                generateJavaAccessor(clazz, rawHtml, rawCss);
                continue; // Terminamos con esta clase, pasamos a la siguiente
            }

            if (rawHtml == null && tplMethod != null) {
                rawHtml = extractTemplateString(clazz, tplMethod);
            }

            if (rawHtml != null) {
               rawHtml = rawHtml.stripIndent().trim();
                 
               Element errorLocation = tplMethod != null ? tplMethod : clazz;
               boolean isValid = validateTemplateConnections(rawHtml, clazz, errorLocation);
                    
               if(isValid) {
                  generateHtmlResource(clazz, rawHtml);
                  generateClientJs(clazz, rawHtml);
                  generateJavaAccessor(clazz, rawHtml, rawCss);
               }
                
           } else if (shouldGenerateAccessor(clazz)) {
                
                // 1. Verificamos si la clase es abstracta (como AppPage)
                if (clazz.getModifiers().contains(Modifier.ABSTRACT)) {
                    // Es normal que no tenga HTML. Generamos su Accessor silenciosamente
                    // para que sus hijos hereden las propiedades por AOT.
                    generateJavaAccessor(clazz, rawHtml, rawCss);
                } else {
                    // 2. 🔥 AHORA SÍ: Es una clase CONCRETA que el developer va a usar,
                    // y olvidó su template. ¡Explotamos sin piedad!
                    messager.printMessage(Diagnostic.Kind.ERROR, 
                        "❌ [JReactive] ERROR FATAL: No se encontró el template HTML para el componente '" + clazz.getSimpleName() + "'. " +
                        "Debes sobrescribir el método 'template()' o crear el archivo '" + clazz.getSimpleName() + ".html' en su paquete.", clazz);
                }
            }
        }
        return false;
    }

 // 🔥 Agregamos ExecutableElement tplMethod a los parámetros
 // 🔥 Ahora devuelve boolean
    private boolean validateTemplateConnections(String html, TypeElement clazz, Element tplMethod) {
        
        String fileName = clazz.getSimpleName() + ".java";
        boolean hasErrors = false;

        try {
            com.ciro.jreactive.ast.JrxParser.parse(html);
        } catch (IllegalStateException ex) {
            messager.printMessage(Diagnostic.Kind.ERROR, 
                "❌ [JReactive] Error de sintaxis HTML en " + fileName + " : " + ex.getMessage(), tplMethod);
            return false; // 🔥 Detenemos todo, el HTML es irrecuperable
        }

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
                    "❌ [JReactive] La variable '" + rootVar + "' no existe en " + fileName + ". Declárala con @State o expose.", tplMethod);
                hasErrors = true;
            }
        }

        Matcher mEvt = Pattern.compile("@\\w+=\"([\\w.-]+)\\s*\\(?").matcher(html);
        while (mEvt.find()) {
            String fullCall = mEvt.group(1); 
            if (fullCall.contains(".")) {
                String refName = fullCall.split("\\.")[0];
                if (!validRefs.contains(refName)) {
                    messager.printMessage(Diagnostic.Kind.ERROR, 
                        "❌ [JReactive] La referencia '" + refName + "' en el método '" + fullCall + "' no existe. Añade ref=\"" + refName + "\". (" + fileName + ")", tplMethod);
                    hasErrors = true;
                }
            } else if (!validMethods.contains(fullCall)) {
                messager.printMessage(Diagnostic.Kind.ERROR, 
                    "❌ [JReactive] El método '" + fullCall + "' no existe en " + fileName + ". Falta @Call.", tplMethod);
                hasErrors = true;
            }
        }
        
        return !hasErrors;
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
    	
    	if (clazz.getAnnotation(com.ciro.jreactive.annotations.WebComponent.class) != null) return true;
    	
        if (findTemplateMethod(clazz) != null) return true;

        TypeElement current = clazz;
        while (current != null && !current.getQualifiedName().toString().equals("java.lang.Object")) {
            String superName = current.getSuperclass().toString();
            if (superName.contains("HtmlComponent") || superName.contains("ViewLeaf")) {
                return true;
            }
            
            TypeMirror superClassMirror = current.getSuperclass();
            if (superClassMirror.getKind() == TypeKind.NONE) break;
            current = (TypeElement) processingEnv.getTypeUtils().asElement(superClassMirror);
        }
        return false;
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
        // 🔥 FIX: FQCN en lugar de simpleName
        String safeName = clazz.getQualifiedName().toString().replace(".", "_");
        try {
            FileObject fileObject = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "static/jrx/templates/" + safeName + ".html");
            try (Writer writer = fileObject.openWriter()) { writer.write(htmlContent); }
        } catch (IOException e) { 
            messager.printMessage(Diagnostic.Kind.ERROR, "Error generando HTML para " + safeName + ": " + e);
        }
    }

    private void generateClientJs(TypeElement cls, String html) {
        // 🔥 FIX: FQCN para el archivo JS y el objeto window
        String safeName = cls.getQualifiedName().toString().replace(".", "_");
        String fileName = "static/js/jrx/" + safeName + ".jrx.js";
        
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
                writer.write("window.JRX_RENDERERS['" + safeName + "'] = {\n");
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
                    "Error generando client JS para " + safeName + ": " + e.getMessage(),
                    cls);
        }
    }

    private void generateJavaAccessor(TypeElement clazz, String rawHtml, String rawCss) {
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
                
                // 🔥 LLAMAMOS A LOS DOS NUEVOS MOTORES AOT
                generateAstMethod(w, rawHtml);
                generateCssMethod(w, clazz, rawCss);

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
        // 🔥 ELIMINAMOS EL SILENCIO: Si la propiedad no está en el switch, lanzamos error.
        w.write("            throw new IllegalArgumentException(\"❌ [JReactive] Propiedad '\" + p + \"' no mapeada en \" + t.getClass().getSimpleName());\n");
        w.write("        } catch (NullPointerException e) {\n");
        // 🔥 Si intentamos hacer `user.address.street = 'x'` pero `address` es null, explicamos exactamente qué pasó.
        w.write("            throw new IllegalStateException(\"❌ [JReactive] Intentaste escribir en '\" + p + \"', pero un objeto padre es NULL en \" + t.getClass().getSimpleName(), e);\n");
        w.write("        } catch (Exception e) {\n");
        w.write("            throw new RuntimeException(\"❌ [JReactive] Error escribiendo la propiedad '\" + p + \"'\", e);\n");
        w.write("        }\n");
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
        // Mantenemos el NullPointerException tragado SOLO para lectura (Esto es el Optional Chaining,
        // permite que {{user.address.street}} no rompa la pantalla si address es null).
        w.write("        } catch (NullPointerException e) { return null; } \n");
        // Pero si es CUALQUIER otro error (ClassCast, IndexOutOfBounds, etc), hacemos ruido en el log.
        w.write("        catch (Exception e) { \n");
        w.write("            System.err.println(\"⚠️ [JReactive] Error leyendo la propiedad '\" + p + \"' en \" + t.getClass().getSimpleName() + \": \" + e.getMessage());\n");
        w.write("        }\n");
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
                /*
                if (isWrite) {
                	//map.put(rootKey, rootAccess + " = " + castLogic(e.asType(), "v"));

                    map.put(rootKey, "((com.ciro.jreactive.ReactiveVar<Object>)t.getRawBindings().get(\"" + rootKey + "\")).set(" + castLogic(e.asType(), "v") + ")");
                } else {
                    map.put(rootKey, "unwrap(" + rootAccess + ")");
                }
                */
                
                if (isWrite) {
                    String typeStr = e.asType().toString();
                    // 🔥 SI ES UN WRAPPER (Type o ReactiveVar), USAMOS .set()
                    if (typeStr.contains("com.ciro.jreactive.ReactiveVar") || typeStr.contains("com.ciro.jreactive.Type")) {
                        javax.lang.model.type.TypeMirror innerType = getInnerType(e.asType());
                        map.put(rootKey, "if (" + rootAccess + " != null) { " + rootAccess + ".set(" + castLogic(innerType, "v") + "); }");
                    } else {
                        // SI ES PRIMITIVO O POJO, ASIGNACIÓN DIRECTA
                        map.put(rootKey, rootAccess + " = " + castLogic(e.asType(), "v"));
                    }
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

    /*
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
    }*/
    private String castLogic(javax.lang.model.type.TypeMirror type, String varName) {
        String t = getErasure(type);
        
        // 1. Manejo de BOOLEAN (Primitivo o Boxed)
        if (t.equals("java.lang.Boolean") || type.getKind() == javax.lang.model.type.TypeKind.BOOLEAN) {
            return "(" + varName + " instanceof String ? Boolean.valueOf((String)" + varName + ") : (Boolean)" + varName + ")";
        }
        
        // 2. Manejo de INT (Primitivo o Boxed)
        if (t.equals("java.lang.Integer") || type.getKind() == javax.lang.model.type.TypeKind.INT) {
            return "(" + varName + " instanceof String ? Integer.valueOf((String)" + varName + ") : ((Number)" + varName + ").intValue())";
        }

        // 3. Manejo de LONG (Primitivo o Boxed)
        if (t.equals("java.lang.Long") || type.getKind() == javax.lang.model.type.TypeKind.LONG) {
            return "(" + varName + " instanceof String ? Long.valueOf((String)" + varName + ") : ((Number)" + varName + ").longValue())";
        }

        // 4. Otros primitivos (Double, Float, etc.)
        if (type.getKind().isPrimitive()) {
            return switch(type.getKind()) {
                case DOUBLE -> "((Number)" + varName + ").doubleValue()";
                default -> "(" + t + ")" + varName;
            };
        }
        
        // 5. Fallback para objetos normales
        return "(" + t + ")" + varName;
    }

    private boolean isValidField(Element e) {
        return e.getKind() == ElementKind.FIELD && 
               !e.getModifiers().contains(Modifier.PRIVATE) && 
               !e.getModifiers().contains(Modifier.STATIC) &&
               // 🔥 Agregamos Prop.class a los campos válidos
               (e.getAnnotation(State.class) != null || 
                e.getAnnotation(Bind.class) != null || 
                e.getAnnotation(Prop.class) != null ||
               e.getAnnotation(Shared.class) != null);
    }

    private String getBindKey(Element e) {
        State s = e.getAnnotation(State.class);
        Bind b = e.getAnnotation(Bind.class);
        Prop p = e.getAnnotation(Prop.class); 
        Shared sh = e.getAnnotation(Shared.class);
        
        String name = e.getSimpleName().toString();
        
        if (s != null && !s.value().isBlank()) return s.value();
        if (b != null && !b.value().isBlank()) return b.value();
        if (p != null && !p.value().isBlank()) return p.value(); 
        
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

            // 1. Intentar por fuente actual
            if (type != null) {
                ExecutableElement tplMethod = findTemplateMethod(type);
                if (tplMethod != null) {
                    template = extractTemplateString(type, tplMethod);
                    if (template != null) {
                        template = template.stripIndent().trim();
                    }
                }
            }

            // 2. Fallback por recurso generado
            if (template == null || template.isBlank()) {
                // 🔥 FIX: Buscar por el nombre seguro (FQCN con guiones bajos)
                String fqcn = type != null ? type.getQualifiedName().toString() : (ownerPackage + "." + simpleName);
                String safeName = fqcn.replace(".", "_");
                template = tryLoadGeneratedTemplate(safeName);
                
                // Fallback de retrocompatibilidad (por si hay componentes viejos)
                if (template == null || template.isBlank()) {
                    template = tryLoadGeneratedTemplate(simpleName);
                }
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
    
    private String tryReadHtmlFile(TypeElement clazz) {
        String pkg = processingEnv.getElementUtils().getPackageOf(clazz).getQualifiedName().toString();
        String fileName = clazz.getSimpleName().toString() + ".html";
        
        try {
            FileObject fileObject = filer.getResource(StandardLocation.SOURCE_PATH, pkg, fileName);
            CharSequence content = fileObject.getCharContent(true);
            if (content != null) return content.toString();
        } catch (Exception e) {
            messager.printMessage(Diagnostic.Kind.NOTE, 
                "ℹ️ [JReactive] No se encontró " + fileName + " en SOURCE_PATH: " + e.getMessage());
        }

        try {
            FileObject fileObject = filer.getResource(StandardLocation.CLASS_PATH, pkg, fileName);
            CharSequence content = fileObject.getCharContent(true);
            if (content != null) return content.toString();
        } catch (Exception e) {
            messager.printMessage(Diagnostic.Kind.NOTE, 
                "ℹ️ [JReactive] No se encontró " + fileName + " en CLASS_PATH: " + e.getMessage());
        }

        return null;
    }
    
    private String tryReadCssFile(TypeElement clazz) {
        String pkg = processingEnv.getElementUtils().getPackageOf(clazz).getQualifiedName().toString();
        String fileName = clazz.getSimpleName().toString() + ".css";

        try {
            FileObject fileObject = filer.getResource(StandardLocation.SOURCE_PATH, pkg, fileName);
            CharSequence content = fileObject.getCharContent(true);
            if (content != null) return content.toString();
        } catch (Exception e) {
            messager.printMessage(Diagnostic.Kind.NOTE, 
                "ℹ️ [JReactive] No se encontró " + fileName + " en SOURCE_PATH: " + e.getMessage());
        }

        try {
            FileObject fileObject = filer.getResource(StandardLocation.CLASS_PATH, pkg, fileName);
            CharSequence content = fileObject.getCharContent(true);
            if (content != null) return content.toString();
        } catch (Exception e) {
            messager.printMessage(Diagnostic.Kind.NOTE, 
                "ℹ️ [JReactive] No se encontró " + fileName + " en CLASS_PATH: " + e.getMessage());
        }

        return null;
    }
    
    
    
 // =========================================================================
    // 🔥 PRE-MASTICADORES AOT (AST y CSS)
    // =========================================================================

    private void generateAstMethod(Writer w, String html) throws IOException {
        w.write("    @Override\n");
        w.write("    public java.util.List<com.ciro.jreactive.ast.JrxNode> getAst() {\n");
        if (html == null || html.isBlank()) {
            w.write("        return null;\n    }\n\n");
            return;
        }
        try {
            List<com.ciro.jreactive.ast.JrxNode> nodes = com.ciro.jreactive.ast.JrxParser.parse(html);
            w.write("        return java.util.Arrays.asList(\n");
            for (int i = 0; i < nodes.size(); i++) {
                writeNodeCode(w, nodes.get(i), 3);
                if (i < nodes.size() - 1) w.write(",\n");
            }
            w.write("\n        );\n");
        } catch (Exception e) {
            w.write("        return null;\n"); // Fallback seguro
        }
        w.write("    }\n\n");
    }

    private void writeNodeCode(Writer w, com.ciro.jreactive.ast.JrxNode n, int indent) throws IOException {
        String ind = "    ".repeat(indent);
        if (n instanceof com.ciro.jreactive.ast.TextNode txt) {
            w.write(ind + "new com.ciro.jreactive.ast.TextNode(\"" + escapeJavaString(txt.text) + "\")");
        } else if (n instanceof com.ciro.jreactive.ast.ElementNode el) {
            boolean isComp = n instanceof com.ciro.jreactive.ast.ComponentNode;
            String className = isComp ? "ComponentNode" : "ElementNode";
            w.write(ind + "new com.ciro.jreactive.ast." + className + "(\"" + escapeJavaString(el.tagName) + "\", " + el.isSelfClosing + ")");
            if (!el.attributes.isEmpty() || !el.children.isEmpty()) {
                w.write(" {{\n");
                for (Map.Entry<String, String> attr : el.attributes.entrySet()) {
                    w.write(ind + "    attributes.put(\"" + escapeJavaString(attr.getKey()) + "\", \"" + escapeJavaString(attr.getValue()) + "\");\n");
                }
                for (com.ciro.jreactive.ast.JrxNode child : el.children) {
                    w.write(ind + "    children.add(\n");
                    writeNodeCode(w, child, indent + 2);
                    w.write("\n" + ind + "    );\n");
                }
                w.write(ind + "}}");
            }
        } else if (n instanceof com.ciro.jreactive.ast.IfNode ifn) {
            w.write(ind + "new com.ciro.jreactive.ast.IfNode(\"" + escapeJavaString(ifn.condition) + "\") {{\n");
            for (com.ciro.jreactive.ast.JrxNode child : ifn.trueBranch) {
                w.write(ind + "    trueBranch.add(\n");
                writeNodeCode(w, child, indent + 2);
                w.write("\n" + ind + "    );\n");
            }
            ifn.falseBranch.forEach(child -> {
                try {
                    w.write(ind + "    falseBranch.add(\n");
                    writeNodeCode(w, child, indent + 2);
                    w.write("\n" + ind + "    );\n");
                } catch (IOException e) {
                	System.err.println("❌ [JReactive APT] Error FATAL escribiendo la rama 'else' del AST en disco: " + e.getMessage());
                }
            });
            w.write(ind + "}}");
        } else if (n instanceof com.ciro.jreactive.ast.EachNode each) {
            w.write(ind + "new com.ciro.jreactive.ast.EachNode(\"" + escapeJavaString(each.listExpression) + "\", \"" + escapeJavaString(each.alias) + "\") {{\n");
            for (com.ciro.jreactive.ast.JrxNode child : each.children) {
                w.write(ind + "    children.add(\n");
                writeNodeCode(w, child, indent + 2);
                w.write("\n" + ind + "    );\n");
            }
            w.write(ind + "}}");
        }
    }

    private void generateCssMethod(Writer w, TypeElement clazz, String rawCss) throws IOException {
        w.write("    @Override\n");
        w.write("    public String getScopedCss() {\n");
        if (rawCss == null || rawCss.isBlank()) {
            w.write("        return \"\";\n    }\n\n");
            return;
        }
        String scopeId = "jrx-sc-" + clazz.getSimpleName().toString();
        // 🔥 AOT CSS: Compilamos y minificamos durante el 'mvn install'
        String scoped = CssScoper.scope(rawCss, scopeId);
        w.write("        return \"" + escapeJavaString(scoped) + "\";\n");
        w.write("    }\n\n");
    }

    private String escapeJavaString(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
    
 // 🔥 NUEVO: Generador automático del template para Web Components
    private String generateWebComponentHtml(com.ciro.jreactive.annotations.WebComponent wc) {
        StringBuilder sb = new StringBuilder();
        sb.append("<").append(wc.tag());

        // 1. Inyectar Props (:prop="{{prop}}")
        for (String prop : wc.props()) {
            sb.append(" :").append(prop).append("=\"{{").append(prop).append("}}\"");
        }

        // 2. Inyectar Eventos (@evento="{{onEvento}}")
        for (String evt : wc.events()) {
            // Convierte "sl-change" a "onSlChange" para el método Java
            String camelCase = java.util.Arrays.stream(evt.split("-"))
                .map(p -> p.substring(0, 1).toUpperCase() + p.substring(1))
                .collect(java.util.stream.Collectors.joining());
            
            sb.append(" @").append(evt).append("=\"{{on").append(camelCase).append("}}\"");
        }
        sb.append(">\n");

        // 3. Inyectar Slots
        for (String slot : wc.slots()) {
            if ("default".equals(slot) || "".equals(slot)) {
                sb.append("  <slot/>\n");
            } else {
                // El truco del display:contents para Web Components
                sb.append("  <div slot=\"").append(slot).append("\" style=\"display: contents;\">\n");
                sb.append("    <slot name=\"").append(slot).append("\"/>\n");
                sb.append("  </div>\n");
            }
        }

        sb.append("</").append(wc.tag()).append(">");
        return sb.toString();
    }
    
}