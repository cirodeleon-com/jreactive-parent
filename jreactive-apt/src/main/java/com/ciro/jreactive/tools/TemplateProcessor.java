package com.ciro.jreactive.tools;

import com.ciro.jreactive.Bind;
import com.ciro.jreactive.State;
import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.annotations.Client;
import com.google.auto.service.AutoService;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.sun.source.tree.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
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
    private final Set<String> _generatedAccessors = new HashSet<>();
    private final Set<String> _generatedClientJs = new HashSet<>();
    private int __nodeSeq = 0;

    // 🔥 REGEX TOKENIZADOR (Solo identifica tokens, la estructura la arma el Stack)
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "\\{\\{\\s*(#if|#each|/if|/each|else)\\s*([^}]*)\\s*}}|" + // Grupo 1, 2: Bloques
            "\\{\\{\\s*([\\w#.!-]+(?:\\.[\\w-]+)*)\\s*}}|" +           // Grupo 3: Variables
            "<([A-Z][\\w]*|slot)(\\s+[^>]*)?(/?)>|" +                   // Grupo 4, 5, 6: Apertura Componente
            "</([A-Z][\\w]*|slot)>",                                    // Grupo 7: Cierre Componente
            Pattern.DOTALL
    );

    private static final Pattern HTML5_VOID_FIX = Pattern.compile(
            "<(area|base|br|col|embed|hr|img|input|link|meta|param|source|track|wbr)([^>]*?)(?<!/)>",
            Pattern.CASE_INSENSITIVE);

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        try {
            this.trees = Trees.instance(env);
        } catch (Throwable t) {
            // Ignorar en entornos limitados
        }
        this.filer = env.getFiler();
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
                    // Limpieza de indentación (Java 15+)
                    rawHtml = rawHtml.stripIndent().trim();

                    // 2. Pre-procesar eventos (@click -> data-call-click)
                    String processedHtml = qualifyEventsAtCompileTime(rawHtml);

                    // 3. Generar archivo físico .html
                    generateHtmlResource(clazz, processedHtml);

                    // 4. Generar JS para cliente (@Client)
                    //if (clazz.getAnnotation(Client.class) != null) {
                        generateClientJs(clazz, processedHtml);
                    //}

                    // 5. Generar Accessor Java
                    if (shouldGenerateAccessor(clazz)) {
                        generateJavaAccessor(clazz, processedHtml);
                    }
                }
            } else if (shouldGenerateAccessor(clazz)) {
                // Componente sin template pero con estado
                generateJavaAccessor(clazz, null);
            }
        }
        return false;
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
        } catch (IOException e) { }
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
                writer.write("  getTemplate: function() {\n");
                String escapedHtml = html
                        .replace("\\", "\\\\")
                        .replace("`", "\\`")
                        .replace("${", "\\${")
                        .replace("\r", "");
                writer.write("    return `" + escapedHtml + "`;\n");
                writer.write("  }\n");

                writer.write("};\n");
                
            }
        } catch (IOException e) { 
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Error generando JS: " + e);
        }
    }

    private void generateJavaAccessor(TypeElement clazz, String processedHtml) {
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

                if (processedHtml != null) {
                    generateRenderStatic(w, clazz, className, processedHtml);
                } else {
                    w.write("    @Override public String renderStatic(" + className + " t) { return null; }\n");
                }

                w.write("}\n");
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    // --- Métodos Reflection Optimizado ---

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

    // --- Render AOT (Stack Parser) ---

    private void generateRenderStatic(Writer w, TypeElement clazz, String className, String rawHtml) throws IOException {
        w.write("    @Override\n");
        w.write("    public String renderStatic(" + className + " t) {\n");

        if (clazz.getAnnotation(Client.class) != null) {
            w.write("        return t._getBundledResources() + \"<div id=\\\"\" + t.getId() + \"\\\" data-jrx-client=\\\"" + className + "\\\"></div>\";\n");
            w.write("    }\n");
            return;
        }

        w.write("        try {\n");
        w.write("            StringBuilder sb = new StringBuilder(" + (int)(rawHtml.length() * 1.5) + ");\n");
        w.write("            sb.append(\"<div id=\\\"\").append(t.getId()).append(\"\\\">\");\n");
        w.write("            sb.append(t._getBundledResources());\n");

        this.__nodeSeq = 0;
        List<Node> nodes = parseToNodes(rawHtml);
        Set<String> aliases = new HashSet<>();
        
        for (Node node : nodes) {
            node.generate(w, aliases, "sb");
        }

        w.write("            sb.append(\"</div>\");\n");
        w.write("            return sb.toString();\n");
        w.write("        } catch (Exception e) { return \"Render Error: \" + e.getMessage(); }\n");
        w.write("    }\n\n");

        // Helper optimizado
        w.write("    private Object resolvePath(Object obj, String path) {\n");
        w.write("        if (obj == null || path.isEmpty()) return obj;\n");
        w.write("        try {\n");
        w.write("            for (String p : path.split(\"\\\\.\")) {\n");
        w.write("                if (obj == null) return null;\n");
        w.write("                if (obj instanceof com.ciro.jreactive.ReactiveVar) obj = ((com.ciro.jreactive.ReactiveVar)obj).get();\n");
        w.write("                java.lang.reflect.Field f = findField(obj.getClass(), p);\n");
        w.write("                if (f != null) {\n");
        w.write("                    f.setAccessible(true);\n");
        w.write("                    obj = f.get(obj);\n");
        w.write("                } else return null;\n");
        w.write("            }\n");
        w.write("            if (obj instanceof com.ciro.jreactive.ReactiveVar) return ((com.ciro.jreactive.ReactiveVar)obj).get();\n");
        w.write("            return obj;\n");
        w.write("        } catch (Exception e) { return null; }\n");
        w.write("    }\n");
        w.write("    private java.lang.reflect.Field findField(Class<?> c, String n) {\n");
        w.write("        try { return c.getDeclaredField(n); } catch (Exception e) {\n");
        w.write("            return (c.getSuperclass() != null) ? findField(c.getSuperclass(), n) : null;\n");
        w.write("        }\n");
        w.write("    }\n");
    }

    // --- Parser Stack ---
    private List<Node> parseToNodes(String html) {
        List<Node> rootNodes = new ArrayList<>();
        if (html == null || html.isEmpty()) return rootNodes;

        Stack<ContainerNode> stack = new Stack<>();
        ContainerNode root = new ContainerNode(rootNodes);
        stack.push(root);

        Matcher m = TOKEN_PATTERN.matcher(html);
        int lastIdx = 0;

        while (m.find()) {
            String text = html.substring(lastIdx, m.start());
            if (!text.isEmpty()) stack.peek().add(new TextNode_(text));

            String blockTag = m.group(1); 
            String varTag = m.group(3);
            String openTag = m.group(4);
            String closeTag = m.group(7);

            if (blockTag != null) {
                handleBlock(blockTag, m.group(2), stack);
            } 
            else if (varTag != null) {
                stack.peek().add(new VarNode(varTag));
            }
            else if (openTag != null) {
                String attrs = m.group(5);
                boolean selfClosing = "/".equals(m.group(6));
                
                ComponentNode comp = new ComponentNode(openTag, attrs, new ArrayList<>(), __nodeSeq++);
                stack.peek().add(comp);
                
                // Si NO es autocierre y NO es slot, entramos al nivel
                if (!selfClosing && !openTag.equals("slot")) {
                    stack.push(comp);
                }
            }
            else if (closeTag != null) {
                if (!stack.isEmpty() && stack.peek() instanceof ComponentNode cNode && cNode.name.equals(closeTag)) {
                    stack.pop();
                }
            }
            lastIdx = m.end();
        }
        String tail = html.substring(lastIdx);
        if (!tail.isEmpty()) stack.peek().add(new TextNode_(tail));

        return rootNodes;
    }

    private void handleBlock(String tag, String args, Stack<ContainerNode> stack) {
        if (tag.startsWith("#if")) {
            IfNode node = new IfNode(args.trim(), new ArrayList<>(), new ArrayList<>());
            stack.peek().add(node);
            stack.push(node);
        } else if (tag.startsWith("#each")) {
            String[] parts = args.split(" as ");
            // 🔥 ID único para variables del loop
            EachNode node = new EachNode(parts[0].trim(), parts.length > 1 ? parts[1].trim() : "it", new ArrayList<>(), __nodeSeq++);
            stack.peek().add(node);
            stack.push(node);
        } else if (tag.equals("else")) {
            if (!stack.isEmpty() && stack.peek() instanceof IfNode ifNode) {
                ifNode.switchToElse();
            }
        } else if (tag.startsWith("/")) {
            if (stack.size() > 1) stack.pop();
        }
    }

    // --- Node Structures ---

    interface Node { 
        void generate(Writer w, Set<String> aliases, String sb) throws IOException;
        String generateRaw(); 
    }

    static class ContainerNode implements Node {
        List<Node> children;
        public ContainerNode(List<Node> c) { this.children = c; }
        public void add(Node n) { children.add(n); }
        public void generate(Writer w, Set<String> a, String sb) throws IOException {
            for(Node n : children) n.generate(w, a, sb);
        }
        public String generateRaw() {
            StringBuilder sb = new StringBuilder();
            for(Node n : children) sb.append(n.generateRaw());
            return sb.toString();
        }
    }

    record TextNode_(String text) implements Node {
        public void generate(Writer w, Set<String> a, String sb) throws IOException {
            w.write("            " + sb + ".append(\"" + escapeJava(text) + "\");\n");
        }
        public String generateRaw() { return text; }
    }

    record VarNode(String path) implements Node {
        public void generate(Writer w, Set<String> a, String sb) throws IOException {
            if (path.equals("this.id") || path.equals("id")) {
                w.write("            " + sb + ".append(t.getId());\n");
                return;
            }
            String root = path.split("\\.")[0];
            if (a.contains(root)) {
                String sub = path.contains(".") ? path.substring(root.length() + 1) : "";
                w.write("            " + sb + ".append(com.ciro.jreactive.HtmlEscaper.escape(java.util.Objects.toString(resolvePath(" + root + ", \"" + sub + "\"), \"\")));\n");
            } else {
                w.write("            " + sb + ".append(\"{{\" + t.getId() + \"." + path + "}}\");\n");
            }
        }
        public String generateRaw() { return "{{" + path + "}}"; }
    }

    static class ComponentNode extends ContainerNode {
        String name; String attrs; int uid;
        private static final Pattern ATTR_REGEX = Pattern.compile("([:a-zA-Z0-9_@-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\"'\\s>]+))");
        public ComponentNode(String n, String a, List<Node> c, int u) { super(c); name=n; attrs=a; uid=u; }

        public void generate(Writer w, Set<String> a, String sb) throws IOException {
            if (name.equals("slot")) { 
                w.write("            " + sb + ".append(t._getSlotHtml());\n");
                return;
            }
            String mapVar = "_attrs_" + uid;
            String slotVar = "_slot_" + uid;
            String slotSb = "_sbSlot_" + uid;
            
            w.write("            {\n");
            w.write("                java.util.Map<String, String> " + mapVar + " = new java.util.HashMap<>();\n");
            if (attrs != null) {
                Matcher m = ATTR_REGEX.matcher(attrs);
                while(m.find()) {
                    String k = m.group(1);
                    String v = m.group(2)!=null?m.group(2):m.group(3)!=null?m.group(3):m.group(4);
                    if(v==null) v="";
                    if (k.startsWith(":") || v.contains("{{")) {
                        String key = k.startsWith(":") ? k.substring(1) : k;
                        String root = v.replaceAll("[^\\w.]", "").split("\\.")[0];
                        if (!a.contains(root) && !isLiteral(v)) {
                            w.write("                " + mapVar + ".put(\"" + key + "\", t.getId() + \"." + escapeJava(v) + "\");\n");
                        } else {
                            w.write("                " + mapVar + ".put(\"" + key + "\", \"" + escapeJava(v) + "\");\n");
                        }
                    } else {
                        w.write("                " + mapVar + ".put(\"" + k + "\", \"" + escapeJava(v) + "\");\n");
                    }
                }
            }

            w.write("                String " + slotVar + " = \"\";\n");
            if (!children.isEmpty()) {
                w.write("                StringBuilder " + slotSb + " = new StringBuilder();\n");
                super.generate(w, a, slotSb);
                w.write("                " + slotVar + " = " + slotSb + ".toString();\n");
            }
            w.write("                " + sb + ".append(t.renderChild(\"" + name + "\", " + mapVar + ", " + slotVar + "));\n");
            w.write("            }\n");
        }
        public String generateRaw() { return ""; } // Componentes anidados no generan raw en el padre
        private boolean isLiteral(String s) { return s.matches("true|false|-?\\d+(\\.\\d+)?|'.*'"); }
    }

    static class IfNode extends ContainerNode {
        String cond; List<Node> elseChildren; boolean inElse = false;
        public IfNode(String c, List<Node> ch, List<Node> ech) { super(ch); cond=c; elseChildren=ech; }
        public void switchToElse() { inElse = true; }
        public void add(Node n) { if(inElse) elseChildren.add(n); else super.add(n); }

        public void generate(Writer w, Set<String> a, String sb) throws IOException {
            // 🔥 DUAL GENERATION: Template Oculto + HTML Visible
            w.write("            " + sb + ".append(\"<template data-if=\\\"" + escapeJava(cond) + "\\\">\");\n");
            w.write("            " + sb + ".append(\"" + escapeJava(super.generateRaw()) + "\");\n");
            w.write("            " + sb + ".append(\"</template>\");\n");
            
            w.write("            if (com.ciro.jreactive.template.TemplateContext.evalSimple(t, \"" + escapeJava(cond) + "\")) {\n");
            super.generate(w, a, sb);
            w.write("            }\n");

            if (!elseChildren.isEmpty()) {
                StringBuilder rawElse = new StringBuilder();
                for(Node n : elseChildren) rawElse.append(n.generateRaw());

                w.write("            " + sb + ".append(\"<template data-else=\\\"" + escapeJava(cond) + "\\\">\");\n");
                w.write("            " + sb + ".append(\"" + escapeJava(rawElse.toString()) + "\");\n");
                w.write("            " + sb + ".append(\"</template>\");\n");
                
                w.write("            if (!com.ciro.jreactive.template.TemplateContext.evalSimple(t, \"" + escapeJava(cond) + "\")) {\n");
                for(Node n : elseChildren) n.generate(w, a, sb);
                w.write("            }\n");
            }
        }
        public String generateRaw() { return ""; }
    }

    static class EachNode extends ContainerNode {
        String list, alias; int uid;
        public EachNode(String l, String a, List<Node> c, int u) { super(c); list=l; alias=a; uid=u; }
        
        public void generate(Writer w, Set<String> a, String sb) throws IOException {
            // 🔥 DUAL GENERATION: Template Oculto + HTML Visible
            w.write("            " + sb + ".append(\"<template data-each=\\\"" + escapeJava(list) + ":" + alias + "\\\">\");\n");
            w.write("            " + sb + ".append(\"" + escapeJava(generateRaw()) + "\");\n");
            w.write("            " + sb + ".append(\"</template>\");\n");

            // 🔥 FIX: SCOPE para variables únicas (_l_123)
            w.write("            {\n"); 
            String listVar = "_l_" + uid;
            w.write("                Object " + listVar + " = resolvePath(t, \"" + list + "\");\n");
            w.write("                if (" + listVar + " instanceof java.lang.Iterable) {\n");
            w.write("                    for (Object " + alias + " : (java.lang.Iterable) " + listVar + ") {\n");
            Set<String> sub = new HashSet<>(a); sub.add(alias);
            for(Node n : children) n.generate(w, sub, sb);
            w.write("                    }\n                }\n");
            w.write("            }\n");
        }
        
        public String generateRaw() {
            StringBuilder sb = new StringBuilder();
            for(Node n : children) sb.append(n.generateRaw());
            return sb.toString();
        }
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

    private String qualifyEventsAtCompileTime(String html) {
        if (html == null) return "";
        try {
            String clean = HTML5_VOID_FIX.matcher(html).replaceAll("<$1$2/>");
            Document doc = Jsoup.parse(clean, "", Parser.xmlParser());
            doc.outputSettings().prettyPrint(false).syntax(Document.OutputSettings.Syntax.html);

            for (org.jsoup.nodes.Element el : doc.getAllElements()) {
                List<Attribute> toAdd = new ArrayList<>();
                List<String> toRemove = new ArrayList<>();

                for (Attribute attr : el.attributes()) {
                    String k = attr.getKey();
                    if (k.startsWith("@") || k.equals("data-call")) {
                        String v = attr.getValue();
                        if (v.contains("{{") || v.contains("#")) continue;
                        // 🔥 FIX: Usar {{id}} plano para mayor compatibilidad JS
                        String newVal = "{{id}}." + v;

                        if (k.startsWith("@")) {
                            String evt = k.substring(1);
                            toAdd.add(new Attribute("data-call-" + evt, newVal));
                            toRemove.add(k);
                        } else {
                            attr.setValue(newVal);
                        }
                    }
                }
                toRemove.forEach(el::removeAttr);
                toAdd.forEach(a -> el.attr(a.getKey(), a.getValue()));
            }
            return doc.html();
        } catch (Exception e) {
            return html;
        }
    }

    private static String escapeJava(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
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