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
 */
@AutoService(Processor.class)                               // registro automático
@SupportedAnnotationTypes("*")                              // analizamos todo
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public final class TemplateProcessor extends AbstractProcessor {

    private Trees trees;                                    // acceso al AST
    private static final Pattern VAR =
            Pattern.compile("\\{\\{\\s*([\\w#.-]+)\\s*}}"); // {{nombre}}

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        trees = Trees.instance(env);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment round) {

        // Recorremos todas las clases compiladas en este round
        for (Element root : round.getRootElements()) {
            if (root.getKind() != ElementKind.CLASS) continue;

            TypeElement clazz = (TypeElement) root;

            // buscamos el método template() sin parámetros
            clazz.getEnclosedElements().stream()
                 .filter(e -> e.getKind() == ElementKind.METHOD)
                 .map(e -> (ExecutableElement) e)
                 .filter(m -> m.getSimpleName().contentEquals("template"))
                 .filter(m -> m.getParameters().isEmpty())
                 .filter(m -> !m.getModifiers().contains(Modifier.ABSTRACT))
                 .forEach(tpl -> checkTemplate(clazz, tpl));
        }
        return false;                                       // allow other processors
    }

    /* ---------------------------------------------------------- */
    /** Extrae placeholders y comprueba @Bind */
    private void checkTemplate(TypeElement cls, ExecutableElement tpl) {
        /* 1. Obtener el texto del cuerpo de template() ------------- */
        TreePath path       = trees.getPath(tpl);
        MethodTree mt       = (MethodTree) path.getLeaf();
        if (mt.getBody() == null) return;                                // ➋ NUEVO

        String bodySrc = mt.getBody().toString();
        
       

        /* 2. Placeholders {{var}} dentro del String literal -------- */
        Matcher m   = VAR.matcher(bodySrc);
        Set<String> vars = new HashSet<>();
        while (m.find()) vars.add(m.group(1));              // p.e. ClockLeaf#3.clock

        /* 3. Campos @Bind declarados en la clase ------------------- */
        Set<String> binds = cls.getEnclosedElements().stream()
                .filter(f -> f.getKind() == ElementKind.FIELD)
                .filter(f -> f.getAnnotation(Bind.class) != null)
                .map(Element::getSimpleName)
                .map(Object::toString)
                .collect(Collectors.toSet());

        /* 4. Compara y reporta faltantes --------------------------- */
        for (String v : vars) {
            String simple = v.contains(".")   // quitamos namespace si lo hubiera
                         ? v.substring(v.lastIndexOf('.') + 1)
                         : v;
            if (!binds.contains(simple)) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "Variable '{{" + simple + "}}' no declarada con @Bind en "
                        + cls.getSimpleName(),
                        tpl);                 // marca la línea del template()
            }
        }
    }
}


