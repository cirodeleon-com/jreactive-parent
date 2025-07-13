/* src/main/java/com/ciro/jreactive/tools/TemplateProcessor.java */
package com.ciro.jreactive.tools;

import com.ciro.jreactive.Bind;
import com.google.auto.service.AutoService;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.tools.Diagnostic;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

/**
 * Valida que toda variable {{x}} usada en el template()
 * exista como campo anotado con @Bind en la misma clase.
 * Ignora completamente los bloques {{#each…}}{{/each}} y
 * también elimina los placeholders con alias (alias.prop).
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public final class TemplateProcessor extends AbstractProcessor {

    private Trees trees;  // acceso al AST

    /** Busca {{nombre}} */
    private static final Pattern VAR =
        Pattern.compile("\\{\\{\\s*([\\w#.-]+)\\s*}}");

    /** Patrón para ignorar bloques {{#each…}}{{/each}} */
    private static final Pattern EACH_BLOCK_PATTERN =
        Pattern.compile("\\{\\{#each[\\s\\S]*?\\{\\{/each}}");

    /** Patrón para eliminar placeholders con alias (alias.prop) */
    private static final Pattern ALIAS_VAR_PATTERN =
        Pattern.compile("\\{\\{\\s*\\w+\\.(\\w+)\\s*}}");

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        trees = Trees.instance(env);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment round) {
        for (Element root : round.getRootElements()) {
            if (root.getKind() != ElementKind.CLASS) continue;
            TypeElement clazz = (TypeElement) root;

            // Buscar método template() concreto
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

    /** Extrae placeholders y comprueba @Bind, ignorando each y alias */
    private void checkTemplate(TypeElement cls, ExecutableElement tpl) {
        // 1. Obtener el texto del cuerpo de template()
        TreePath path = trees.getPath(tpl);
        MethodTree mt = (MethodTree) path.getLeaf();
        if (mt.getBody() == null) return;

        String bodySrc = mt.getBody().toString();

        // 2. Eliminar completamente bloques {{#each…}}{{/each}}
        bodySrc = EACH_BLOCK_PATTERN.matcher(bodySrc)
                                     .replaceAll("");

        // 3. Eliminar placeholders con alias (alias.prop)
        bodySrc = ALIAS_VAR_PATTERN.matcher(bodySrc)
                                    .replaceAll("");

        // 4. Buscar ahora los {{var}} restantes
        Matcher m = VAR.matcher(bodySrc);
        Set<String> vars = new HashSet<>();
        while (m.find()) {
            vars.add(m.group(1));
        }

        // 5. Recoger los campos @Bind de la clase
        Set<String> binds = cls.getEnclosedElements().stream()
            .filter(f -> f.getKind() == ElementKind.FIELD)
            .filter(f -> f.getAnnotation(Bind.class) != null)
            .map(Element::getSimpleName)
            .map(Object::toString)
            .collect(Collectors.toSet());

        // 6. Validar que cada {{var}} esté en @Bind
        for (String v : vars) {
            String simple = v.contains(".")
                          ? v.substring(v.lastIndexOf('.') + 1)
                          : v;
            if ("this".equals(simple)) continue;  // ignorar {{this}}
            if ("size".equals(simple) || "length".equals(simple)) continue;
            if (!binds.contains(simple)) {
                processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "Variable '{{" + simple + "}}' no declarada con @Bind en "
                      + cls.getSimpleName(),
                    tpl
                );
            }
        }
    }
}

