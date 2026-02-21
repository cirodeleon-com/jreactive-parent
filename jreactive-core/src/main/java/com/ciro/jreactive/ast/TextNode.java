package com.ciro.jreactive.ast;

import com.ciro.jreactive.template.TemplateContext;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextNode implements JrxNode {
    public String text;
    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{\\s*([\\w#.-]+)\\s*}}");

    public TextNode(String text) {
        this.text = text;
    }

    @Override
    public void renderRaw(StringBuilder sb) {
        sb.append(text);
    }

    @Override
    public void render(StringBuilder sb, TemplateContext ctx) {
        if (text == null || !text.contains("{{")) {
            sb.append(text);
            return;
        }

        // 🔥 1. Jsoup inyecta el comentario incondicionalmente, nosotros también.
        sb.append("");

        String currentVal = text;
        Matcher m = VAR_PATTERN.matcher(text);
        String ns = ctx.getComponent().getId() + ".";

        while (m.find()) {
            String token = m.group(0);
            String fullKey = m.group(1);
            
            // 🧹 2. Limpiar el Namespace para que la memoria del servidor lo reconozca
            String localKey = fullKey;
            if (!ns.isEmpty() && localKey.startsWith(ns)) {
                localKey = localKey.substring(ns.length());
            }
            if (localKey.startsWith("this.")) {
                localKey = localKey.substring(5);
            }

            // 🔍 3. Buscar en la memoria
            Object resolvedValue = ctx.resolve(localKey);
            
            if (resolvedValue != null) {
                currentVal = currentVal.replace(token, com.ciro.jreactive.HtmlEscaper.escape(String.valueOf(resolvedValue)));
            } else if (ctx.getComponent().getRawBindings().containsKey(localKey.split("\\.")[0])) {
                // Si la variable existe pero su valor actual es nulo, la borramos de la vista inicial
                currentVal = currentVal.replace(token, "");
            }
            // Si no existe (es del store global), la dejamos intacta para el JS
        }

        // 🛡️ 4. Anti-Amnesia del DOM: Si el texto quedó vacío, metemos un char invisible.
        if (currentVal.isEmpty()) {
            currentVal = "\u200B"; 
        }

        sb.append(currentVal);
    }
}