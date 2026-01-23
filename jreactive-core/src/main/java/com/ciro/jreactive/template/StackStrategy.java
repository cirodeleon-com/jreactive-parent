package com.ciro.jreactive.template;

import com.ciro.jreactive.HtmlComponent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StackStrategy implements TemplateStrategy {
    private final PureTemplateParser parser = new PureTemplateParser();
    private final Map<Class<?>, TemplateNode> cache = new ConcurrentHashMap<>();

    @Override
    public String process(String html, HtmlComponent ctx) {
        // Cacheamos el árbol para que sea instantáneo
        TemplateNode root = cache.computeIfAbsent(ctx.getClass(), k -> parser.parse(html));
        
        StringBuilder sb = new StringBuilder();
        // El contexto puede ir null o vacío, ya no se usa para resolve()
        root.render(sb, null); 
        return sb.toString();
    }
}