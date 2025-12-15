package com.ciro.jreactive;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.parser.Parser;

import java.util.*;
import java.util.regex.Pattern;

public class JsoupComponentEngine extends AbstractComponentEngine {

    private static final Pattern HTML5_VOID_FIX = 
        Pattern.compile("<(area|base|br|col|embed|hr|img|input|link|meta|param|source|track|wbr)([^>]*?)(?<!/)>", Pattern.CASE_INSENSITIVE);

    @Override
    public ComponentEngine.Rendered render(HtmlComponent ctx) {
        List<HtmlComponent> pool = new ArrayList<>(ctx._children());
        ctx._children().clear();
        Map<String, ReactiveVar<?>> all = new HashMap<>();

        String rawTemplate = ctx.template();
        String xmlFriendlyTemplate = HTML5_VOID_FIX.matcher(rawTemplate).replaceAll("<$1$2/>");

        // 1. PARSEAMOS como XML (Mantiene <JCard>, <JInput>, CaseSensitive)
        Document doc = Jsoup.parse(xmlFriendlyTemplate, "", Parser.xmlParser());
        doc.outputSettings().prettyPrint(false);
        
        // 2. 🔥 CAMBIO CLAVE: Exportamos como HTML
        // Esto evita que Jsoup genere tags auto-cerrados (<div />) que rompen el navegador.
        // Como ya parseamos en modo XML, las mayúsculas de los componentes YA están preservadas.
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.html);

        processNodes(doc, ctx, pool, all);

        all.putAll(ctx.selfBindings());
        
        String html = doc.html();
        if (doc.children().size() == 1 && doc.child(0).tagName().equals("#root")) {
             html = doc.child(0).html();
        }

        html = processControlBlocks(html);
        
     // ✅ FIX MEMORIA: Eliminar zombies antes de montar los nuevos
        disposeUnused(pool);

        ctx._mountRecursive();
        return new ComponentEngine.Rendered(html, all);
    }

    private void processNodes(Node node, HtmlComponent ctx, List<HtmlComponent> pool, Map<String, ReactiveVar<?>> all) {
        List<Node> children = new ArrayList<>(node.childNodes());
        for (Node child : children) {
            if (child instanceof Element el && isComponent(el)) {
                String className = el.tagName();
                Map<String, String> attrs = new HashMap<>();
                el.attributes().forEach(a -> attrs.put(a.getKey(), a.getValue()));

                String childHtml = renderChildLogic(ctx, pool, all, className, attrs, el.html());
                
                // Usamos parseXmlFragment para inyectar nodos reales
                List<Node> renderedNodes = Parser.parseXmlFragment(childHtml, "");
                for (Node n : renderedNodes) {
                    child.before(n);
                }
                child.remove();
            } else {
                processNodes(child, ctx, pool, all);
            }
        }
    }

    private boolean isComponent(Element el) {
        return !el.tagName().isEmpty() && Character.isUpperCase(el.tagName().charAt(0));
    }
}