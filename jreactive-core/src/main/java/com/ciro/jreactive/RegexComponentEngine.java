package com.ciro.jreactive;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementación basada en Regex (Original).
 */
final class RegexComponentEngine extends AbstractComponentEngine {

    // Patrones de detección de componentes
    private static final Pattern TAG = Pattern.compile("<\\s*([A-Z][A-Za-z0-9_]*)([^/>]*)/>", Pattern.MULTILINE);
    private static final Pattern PAIR_TAG = Pattern.compile("<\\s*([A-Z][A-Za-z0-9_]*)\\b([^>]*)>([\\s\\S]*?)</\\1>", Pattern.MULTILINE);

    @Override
    public ComponentEngine.Rendered render(HtmlComponent ctx) {
        List<HtmlComponent> pool = new ArrayList<>(ctx._children());
        ctx._children().clear();
        Map<String, ReactiveVar<?>> all = new HashMap<>();

        String template = ctx.template();
        StringBuilder out = new StringBuilder();
        
        // 1. Componentes con par <Tag>...</Tag>
        Matcher pairM = PAIR_TAG.matcher(template);
        StringBuilder tmp = new StringBuilder();
        int cursor = 0;
        while (pairM.find()) {
            tmp.append(template, cursor, pairM.start());
            String className = pairM.group(1);
            String rawAttrs = pairM.group(2);
            String slotHtml = pairM.group(3);

            // Delegamos a la lógica base, parseando props manualmente aquí
            String childHtml = renderChildLogic(ctx, pool, all, className, parseProps(rawAttrs), slotHtml);
            
            tmp.append(childHtml);
            cursor = pairM.end();
        }
        tmp.append(template, cursor, template.length());
        String afterPairs = tmp.toString();

        // 2. Componentes autocontenidos <Tag />
        Matcher m = TAG.matcher(afterPairs);
        cursor = 0;
        while (m.find()) {
            out.append(afterPairs, cursor, m.start());
            String className = m.group(1);
            String rawAttrs = m.group(2);

            String childHtml = renderChildLogic(ctx, pool, all, className, parseProps(rawAttrs), null);
            
            out.append(childHtml);
            cursor = m.end();
        }
        out.append(afterPairs, cursor, afterPairs.length());

        // 3. Finalizar
        all.putAll(ctx.selfBindings());
        String html = processControlBlocks(out.toString()); // Heredado
        
     // ✅ FIX MEMORIA: Eliminar zombies antes de montar los nuevos
        disposeUnused(pool);

        ctx._mountRecursive();
        return new ComponentEngine.Rendered(html, all);
    }

    /** Helper específico de Regex para parsear string "foo='bar'" a Map */
    private Map<String, String> parseProps(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null) return map;
        Matcher mm = Pattern.compile("(\\:?\\w+)\\s*=\\s*\"([^\"]*)\"").matcher(raw);
        while (mm.find()) {
            map.put(mm.group(1), mm.group(2));
        }
        return map;
    }
}