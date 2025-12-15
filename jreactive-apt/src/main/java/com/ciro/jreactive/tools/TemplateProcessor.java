package com.ciro.jreactive.tools;

import com.ciro.jreactive.Bind;
import com.google.auto.service.AutoService;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element; // ✅ Import explícito: "Element" será este
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@AutoService(Processor.class)
@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public final class TemplateProcessor extends AbstractProcessor {

    private static final boolean USE_JSOUP_ENGINE = false; 

    private Trees trees;

    private static final Pattern VAR = Pattern.compile("\\{\\{\\s*([\\w#.-]+)\\s*}}");
    private static final Pattern REF_ALIAS_PATTERN = Pattern.compile("\\bref\\s*=\\s*\"(\\w+)\"");
    private static final Pattern EACH_BLOCK_PATTERN = Pattern.compile("\\{\\{#each[\\s\\S]*?\\{\\{/each}}");
    
    // Regex Legacy
    private static final Pattern ALIAS_VAR_PATTERN = Pattern.compile("\\{\\{\\s*\\w+\\.(\\w+)\\s*}}");
    private static final Pattern PROP_BIND_PATTERN = Pattern.compile(":(\\w[\\w-]*)\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern ATTR_BIND_PATTERN = Pattern.compile("\\s:\\w+\\s*=\\s*\"([^\"]+)\"");

    // Jsoup
    private static final Pattern HTML5_VOID_FIX = 
        Pattern.compile("<(area|base|br|col|embed|hr|img|input|link|meta|param|source|track|wbr)([^>]*?)(?<!/)>", Pattern.CASE_INSENSITIVE);

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        trees = Trees.instance(env);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        for (Element root : round.getRootElements()) {
            if (root.getKind() != ElementKind.CLASS) continue;
            TypeElement clazz = (TypeElement) root;

            clazz.getEnclosedElements().stream()
                 .filter(e -> e.getKind() == ElementKind.METHOD)
                 .map(e -> (ExecutableElement) e)
                 .filter(m -> m.getSimpleName().contentEquals("template"))
                 .filter(m -> m.getParameters().isEmpty())
                 .filter(m -> !m.getModifiers().contains(Modifier.ABSTRACT))
                 .forEach(tpl -> checkTemplate(clazz, tpl));
        }
        return false;
    }

    private void checkTemplate(TypeElement cls, ExecutableElement tpl) {
        TreePath path = trees.getPath(tpl);
        MethodTree mt = (MethodTree) path.getLeaf();
        if (mt.getBody() == null) return;

        String bodySrc = mt.getBody().toString();
        int firstQuote = bodySrc.indexOf("\"");
        int lastQuote  = bodySrc.lastIndexOf("\"");
        if (firstQuote < 0 || lastQuote <= firstQuote) return;
        
        String rawHtml = bodySrc.substring(firstQuote, lastQuote + 1)
                                .replace("\"\"\"", "")
                                .replace("\"", "");

        ParseResult res;

        if (USE_JSOUP_ENGINE) {
            res = parseWithJsoup(rawHtml);
        } else {
            res = parseWithRegex(rawHtml, tpl);
        }

        validateVars(cls, tpl, res.vars, res.refs);
    }

    private ParseResult parseWithRegex(String html, ExecutableElement tpl) {
        Set<String> vars = new HashSet<>();
        Set<String> refs = new HashSet<>();

        String cleanHtml = EACH_BLOCK_PATTERN.matcher(html).replaceAll("");
        cleanHtml = ALIAS_VAR_PATTERN.matcher(cleanHtml).replaceAll("");

        Matcher m = VAR.matcher(cleanHtml);
        while (m.find()) vars.add(m.group(1));

        Matcher p = PROP_BIND_PATTERN.matcher(cleanHtml);
        while (p.find()) {
            String expr = p.group(2).trim();
            String root = expr.contains(".") ? expr.substring(0, expr.indexOf('.')) : expr;
            vars.add(root);
        }
        
        Matcher a = ATTR_BIND_PATTERN.matcher(cleanHtml);
        while (a.find()) vars.add(a.group(1));

        Matcher r = REF_ALIAS_PATTERN.matcher(cleanHtml);
        while (r.find()) {
            String alias = r.group(1);
            if (!refs.add(alias)) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, 
                    "Alias 'ref=\"" + alias + "\"' duplicado en el mismo template", tpl);
            }
        }
        return new ParseResult(vars, refs);
    }

    private ParseResult parseWithJsoup(String html) {
        Set<String> vars = new HashSet<>();
        
        String noEachHtml = EACH_BLOCK_PATTERN.matcher(html).replaceAll("");
        String xmlFriendlyHtml = HTML5_VOID_FIX.matcher(noEachHtml).replaceAll("<$1$2/>");

        Document doc = Jsoup.parse(xmlFriendlyHtml, "", Parser.xmlParser());

        // ⚠️ USAMOS EL NOMBRE COMPLETO PARA EVITAR CONFLICTO
        for (org.jsoup.nodes.Element el : doc.getAllElements()) {
            for (Attribute attr : el.attributes()) {
                String key = attr.getKey();
                String val = attr.getValue();
                if (key.startsWith(":")) {
                    String root = val.contains(".") ? val.split("\\.")[0] : val;
                    vars.add(root); 
                }
                extractVarsFromText(val, vars);
            }
            for (TextNode text : el.textNodes()) {
                extractVarsFromText(text.getWholeText(), vars);
            }
        }

        Set<String> refs = doc.select("[ref]").stream()
                .map(e -> e.attr("ref")).collect(Collectors.toSet());

        return new ParseResult(vars, refs);
    }

    private void validateVars(TypeElement cls, ExecutableElement tpl, Set<String> varsToCheck, Set<String> htmlRefs) {
        Set<String> binds = cls.getEnclosedElements().stream()
            .filter(f -> f.getKind() == ElementKind.FIELD && f.getAnnotation(Bind.class) != null)
            .map(Element::getSimpleName).map(Object::toString).collect(Collectors.toSet());
        
        Set<String> states = cls.getEnclosedElements().stream()
            .filter(f -> f.getKind() == ElementKind.FIELD && f.getAnnotation(com.ciro.jreactive.State.class) != null)
            .map(Element::getSimpleName).map(Object::toString).collect(Collectors.toSet());

        Set<String> childRoots = cls.getEnclosedElements().stream()
            .filter(e -> e.getKind() == ElementKind.CLASS)
            .map(Element::getSimpleName).map(Object::toString).collect(Collectors.toSet());

        Set<String> allRoots = Stream.of(binds, states, childRoots, htmlRefs)
            .flatMap(Set::stream).collect(Collectors.toSet());

        for (String v : varsToCheck) {
            if (v.equals("this") || v.startsWith("#if") || v.startsWith("#else") || v.startsWith("/if")) continue;
            String cleanV = v.replace("!", "").trim();
            if (cleanV.isEmpty()) continue;

            String[] parts = cleanV.split("\\.");
            String root = parts[0];
            String last = parts[parts.length - 1];

            if ("size".equals(last) || "length".equals(last)) continue;

            boolean ok = 
                binds.contains(v)      ||
                binds.contains(last)   || 
                states.contains(root)  ||
                allRoots.contains(root) ||
                "store".equals(root);

            if (!ok) {
                 processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, 
                     "Variable '{{" + cleanV + "}}' no declarada en " + cls.getSimpleName(), tpl);
            }
        }
    }

    private void extractVarsFromText(String text, Set<String> target) {
        Matcher m = VAR.matcher(text);
        while (m.find()) target.add(m.group(1));
    }

    private record ParseResult(Set<String> vars, Set<String> refs) {}
}