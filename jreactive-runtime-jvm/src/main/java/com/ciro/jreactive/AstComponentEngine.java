package com.ciro.jreactive;

import com.ciro.jreactive.ast.*;
import com.ciro.jreactive.template.TemplateContext;
import com.ciro.jreactive.annotations.Client;
import com.ciro.jreactive.annotations.Stateless;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AST-based engine que replica el comportamiento de JsoupComponentEngine.
 * - SSR/CSR
 * - Recursos una sola vez por clase
 * - Token @Stateless
 * - Slot blueprint (sin render prematuro)
 * - Comentarios <!--jrx:...--> para preservar templates en text nodes
 */
public class AstComponentEngine extends AbstractComponentEngine {

    // Igual que Jsoup engine
    private static final Pattern VAR_PATTERN   = Pattern.compile("\\{\\{\\s*([\\w#.-]+)\\s*}}");
    private static final Pattern EVENT_PATTERN = Pattern.compile("^([\\w#.-]+)(?:\\((.*)\\))?$");

    private static final ThreadLocal<RenderSession> SESSION = new ThreadLocal<>();

    private static final class RenderSession {
        int depth = 0;
        final Map<String, ReactiveVar<?>> allBindings = new HashMap<>();
        final Set<String> emittedResources = new HashSet<>();
    }

    public static void installAsDefault() {
        ComponentEngine.setStrategy(new AstComponentEngine());
    }

    @Override
    public ComponentEngine.Rendered render(HtmlComponent ctx) {
        RenderSession s = SESSION.get();
        final boolean rootCall;
        if (s == null) {
            s = new RenderSession();
            SESSION.set(s);
            rootCall = true;
        } else {
            rootCall = false;
        }

        s.depth++;
        try {
            return renderInternal(ctx, s, rootCall);
        } finally {
            s.depth--;
            if (rootCall) {
                SESSION.remove();
            }
        }
    }

    private ComponentEngine.Rendered renderInternal(HtmlComponent ctx, RenderSession s, boolean isRoot) {
        List<HtmlComponent> pool = ctx._getRenderPool();
        if (pool == null) pool = new ArrayList<>();

        // Lifecycle pre-render
        ctx._initIfNeeded();
        ctx._syncState();

        // Registrar bindings del componente al mapa global (y locals si root, igual que Jsoup)
        String prefix = ctx.getId() + ".";
        ctx.getRawBindings().forEach((k, v) -> {
            if (isRoot) s.allBindings.put(k, v);
            s.allBindings.put(prefix + k, v);
        });

        // Recursos una sola vez
        String resources = getResourcesOnce(ctx, s.emittedResources);

        // =============================
        // CSR (@Client)
        // =============================
        if (ctx.getClass().isAnnotationPresent(Client.class)) {
            String id = ctx.getId();
            String name = ctx.getClass().getSimpleName();

            String html = resources + "<div id=\"" + id + "\" data-jrx-client=\"" + name + "\"></div>";

            // Token stateless incluso en shell client
            if (ctx.getClass().isAnnotationPresent(Stateless.class)) {
                html = injectStatelessToken(html, s.allBindings, id);
            }

            collectBindingsRecursive(ctx, s.allBindings);
            disposeUnused(pool);
            ctx._mountRecursive();
            return new ComponentEngine.Rendered(html, s.allBindings);
        }

        // =============================
        // SSR
        // =============================
        String ns = isRoot ? "" : prefix;
        List<JrxNode> ast = JrxParser.parse(ctx.template());

        // scoping: SOLO a los root elements (equivalente a Jsoup)
        addScopeToRootElements(ast, ctx._getScopeId());

        StringBuilder out = new StringBuilder(ctx.template().length() * 2);
        out.append(resources);

        TemplateContext tplCtx = new TemplateContext(ctx);
        Set<String> aliases = new HashSet<>();
        for (JrxNode n : ast) {
            renderNode(n, out, ctx, tplCtx, ns, s, aliases, false);
        }

        String html = out.toString();

        // Token @Stateless en SSR
        if (ctx.getClass().isAnnotationPresent(Stateless.class)) {
            html = injectStatelessToken(html, s.allBindings, null);
        }

        disposeUnused(pool);
        ctx._mountRecursive();

        return new ComponentEngine.Rendered(html, s.allBindings);
    }

    // ------------------------------------------------------------
    // Render dispatch
    // ------------------------------------------------------------
    private void renderNode(
            JrxNode node,
            StringBuilder out,
            HtmlComponent ctx,
            TemplateContext tplCtx,
            String ns,
            RenderSession s,
            Set<String> aliases,
            boolean inTemplateBlueprint
    ) {
        if (node == null) return;

        // Component
        if (node instanceof ComponentNode comp) {
            renderComponent(comp, out, ctx, ns, s, aliases);
            return;
        }

        // ✅ FIX: If/Each deben renderizarse como BLUEPRINT con namespace (igual que Jsoup)
        if (node instanceof IfNode ifNode) {
            renderIfBlueprint(ifNode, out, ctx, ns, s, aliases);
            return;
        }
        if (node instanceof EachNode eachNode) {
            renderEachBlueprint(eachNode, out, ctx, ns, s, aliases);
            return;
        }

        // Element
        if (node instanceof ElementNode el) {
            renderElement(el, out, ctx, tplCtx, ns, s, aliases, inTemplateBlueprint);
            return;
        }

        // Text
        if (node instanceof com.ciro.jreactive.ast.TextNode txt) {
            renderText(txt, out, ctx, ns, aliases, inTemplateBlueprint);
            return;
        }

        // Fallback
        node.render(out, tplCtx);
    }

    // ------------------------------------------------------------
    // Element rendering (HTML normal)
    // ------------------------------------------------------------
    private void renderElement(
            ElementNode el,
            StringBuilder out,
            HtmlComponent ctx,
            TemplateContext tplCtx,
            String ns,
            RenderSession s,
            Set<String> aliases,
            boolean inTemplateBlueprint
    ) {
        // Slot: parsear el HTML del slot y procesarlo con el contexto del componente receptor
        if ("slot".equalsIgnoreCase(el.tagName)) {
            String slotHtml = ctx._getSlotHtml();
            if (slotHtml != null && !slotHtml.isBlank()) {
                List<JrxNode> slotNodes = JrxParser.parse(slotHtml);

                // Render slot nodes con el mismo ctx y ns (igual que Jsoup processNodeTree)
                for (JrxNode sn : slotNodes) {
                	renderNode(sn, out, ctx, tplCtx, ns, s, aliases, inTemplateBlueprint);
                }
            }
            return;
        }

        out.append("<").append(el.tagName);

        // Atributos
        for (Map.Entry<String, String> e : el.attributes.entrySet()) {
            String key = e.getKey();
            String val = e.getValue() == null ? "" : e.getValue();

            // name="field" => namespacing si apunta a binding del ctx
            if ("name".equals(key) && !val.contains("{{")) {
                String root = val.split("\\.")[0];
                if (ctx.getRawBindings().containsKey(root) && !ns.isEmpty() && !val.startsWith(ns)) {
                    val = ns + val;
                }
            }

            // Si estamos en blueprint (template), no resolvemos, solo namespace
            if (inTemplateBlueprint) {
                val = namespaceString(val, ns, ctx, aliases);
                writeAttr(out, key, val);
                continue;
            }

            // Variables {{...}}
            if (val.contains("{{")) {
                val = namespaceString(val, ns, ctx, aliases);
                // Intento de inyección estática (igual que Jsoup injectStaticValue)
                String maybe = resolveAllMustachesIfPossible(val, ctx, ns);
                if (maybe != null) val = maybe;
            }

            // Directiva :prop
            if (key.startsWith(":")) {
                String realKey = key.substring(1);

                // Boolean attrs estilo :disabled, :checked
                if (isBooleanAttr(realKey)) {
                    String expr = namespaceExpression(val, ns, ctx);
                    boolean on = evalBoolExpr(expr, ctx, ns);
                    if (on) {
                        out.append(" ").append(realKey).append("=\"").append(realKey).append("\"");
                    }
                    continue; // no imprimimos :disabled
                }

                // Non-boolean :prop: lo emitimos como prop normal
                // (para HTML nativo suele ser raro, pero mantiene compatibilidad)
                val = namespaceExpression(val, ns, ctx);
                out.append(" ").append(realKey);
                if (!val.isEmpty()) out.append("=\"").append(escapeAttr(val)).append("\"");
                continue;
            }

            // Eventos @click, @submit, data-call
            if (key.startsWith("@") || "data-call".equals(key)) {
                val = rewriteEvent(val, ns, ctx);
            }

            // data-if/data-else/data-each manuales (si el user los usa)
            if ("data-if".equals(key) || "data-else".equals(key)) {
                val = namespaceExpression(val, ns, ctx);
            }
            if ("data-each".equals(key)) {
                String[] parts = val.split(":");
                if (parts.length > 0) {
                    String listExpr = namespaceExpression(parts[0], ns, ctx);
                    val = listExpr + (parts.length > 1 ? ":" + parts[1] : "");
                }
            }

            writeAttr(out, key, val);
        }

        if (el.isSelfClosing) {
            out.append("/>");
            return;
        }

        out.append(">");

        boolean childBlueprint = inTemplateBlueprint || "template".equalsIgnoreCase(el.tagName);

        for (JrxNode child : el.children) {
            renderNode(child, out, ctx, tplCtx, ns, s, aliases, childBlueprint);
        }

        out.append("</").append(el.tagName).append(">");
    }

    // ------------------------------------------------------------
    // Text rendering con <!--jrx:tpl--> (equivalente a Jsoup processTextNode)
    // ------------------------------------------------------------
    private void renderText(
            com.ciro.jreactive.ast.TextNode txt,
            StringBuilder out,
            HtmlComponent ctx,
            String ns,
            Set<String> aliases,
            boolean inTemplateBlueprint
    ) {
        String text = txt.text;
        if (text == null || text.isEmpty()) return;

        if (!text.contains("{{")) {
            out.append(text);
            return;
        }

        String namespacedTpl = namespaceString(text, ns, ctx, aliases);

        // Dentro de blueprint (if/each): NO tocar, solo preservar
        if (inTemplateBlueprint) {
            out.append(namespacedTpl);
            return;
        }

        // Intentar resolver todas las variables
        String currentVal = namespacedTpl;
        Matcher m = VAR_PATTERN.matcher(namespacedTpl);
        boolean fullyResolved = true;

        while (m.find()) {
            String token = m.group(0);
            String fullKey = m.group(1);
            String localKey = fullKey;

            // limpiar namespace del componente actual
            if (!ns.isEmpty() && localKey.startsWith(ns)) {
                localKey = localKey.substring(ns.length());
            }
            if (localKey.startsWith("this.")) localKey = localKey.substring(5);

            ReactiveVar<?> var = ctx.getRawBindings().get(localKey);

            if (var != null && var.get() != null) {
                currentVal = currentVal.replace(token, HtmlEscaper.escape(String.valueOf(var.get())));
            } else {
                fullyResolved = false;
            }
        }

        if (fullyResolved && !currentVal.equals(namespacedTpl)) {
            out.append("<!--jrx:").append(escapeComment(namespacedTpl)).append("-->");
            out.append(currentVal.isEmpty() ? "\u200B" : currentVal);
        } else {
            out.append(namespacedTpl);
        }
    }

    // ------------------------------------------------------------
    // Component rendering: crea hijo, slot blueprint, CSR/SSR
    // ------------------------------------------------------------
    private void renderComponent(
            com.ciro.jreactive.ast.ComponentNode comp,
            StringBuilder out,
            HtmlComponent parent,
            String ns,
            RenderSession s,
            Set<String> aliases
    ) {
        String className = comp.tagName;

        // attrs tal cual (incluye :props para binding server-side)
        Map<String, String> attrs = new LinkedHashMap<>(comp.attributes);

        // 1) Slot blueprint: NO renderizar (para no instanciar componentes del slot “en el padre”)
        //   - aplicar el namespaceAttributesRecursive “guardado” (caso crítico de componentes en slots)
        String slotNs = parent.getId() + ".";
        String slotHtml = buildSlotBlueprint(comp.children, parent, ns, slotNs, aliases);

        // 2) Crear/bind hijo con globalBindings compartido
        List<HtmlComponent> pool = parent._getRenderPool();
        if (pool == null) pool = new ArrayList<>();

        HtmlComponent child = createAndBindComponent(parent, pool, s.allBindings, className, attrs, slotHtml);
        child._initIfNeeded();
        child._syncState();

        // 3) Registrar bindings del hijo prefijados (antes de render)
        String childPrefix = child.getId() + ".";
        child.getRawBindings().forEach((k, v) -> s.allBindings.put(childPrefix + k, v));

        // 4) CSR hijo => shell + resources once + copiar class/style
        if (child.getClass().isAnnotationPresent(Client.class)) {
            String css = getResourcesOnce(child, s.emittedResources);

            String shell = "<div id=\"" + child.getId() + "\" data-jrx-client=\"" + child.getClass().getSimpleName() + "\"";
            if (attrs.containsKey("class")) shell += " class=\"" + escapeAttr(attrs.get("class")) + "\"";
            if (attrs.containsKey("style")) shell += " style=\"" + escapeAttr(attrs.get("style")) + "\"";
            shell += "></div>";

            out.append(css).append(shell);
            return;
        }

        // 5) SSR hijo => delegar a child.render(), pero compartiendo session (ThreadLocal)
        out.append(child.render());
    }

    // ------------------------------------------------------------
    // If/EacH blueprints: serialización manual preservando ":" en attrs
    // ------------------------------------------------------------
    private void renderIfBlueprint(IfNode ifNode, StringBuilder out, HtmlComponent ctx, String ns, RenderSession s, Set<String> aliases) {
        String cond = namespaceExpression(ifNode.condition, ns, ctx);

        out.append("<template data-if=\"").append(escapeAttr(cond)).append("\">");
        for (JrxNode n : ifNode.trueBranch) {
            out.append(serializeBlueprintNode(n, ctx, ns, aliases));
        }
        out.append("</template>");

        if (!ifNode.falseBranch.isEmpty()) {
            out.append("<template data-else=\"").append(escapeAttr(cond)).append("\">");
            for (JrxNode n : ifNode.falseBranch) {
                out.append(serializeBlueprintNode(n, ctx, ns, aliases));
            }
            out.append("</template>");
        }
    }

    private void renderEachBlueprint(EachNode eachNode, StringBuilder out, HtmlComponent ctx, String ns, RenderSession s, Set<String> aliases) {
        String listExpr = namespaceExpression(eachNode.listExpression, ns, ctx);

        out.append("<template data-each=\"")
           .append(escapeAttr(listExpr))
           .append(":")
           .append(escapeAttr(eachNode.alias))
           .append("\">");

        // En each: el alias NO se namespacéa
        Set<String> childAliases = new HashSet<>(aliases);
        childAliases.add(eachNode.alias);

        for (JrxNode n : eachNode.children) {
            out.append(serializeBlueprintNode(n, ctx, ns, childAliases));
        }
        out.append("</template>");
    }

    // ------------------------------------------------------------
    // Slot blueprint builder
    // ------------------------------------------------------------
    private String buildSlotBlueprint(
            List<JrxNode> slotNodes,
            HtmlComponent parentCtx,
            String currentNs,
            String slotNsAlwaysParentId,
            Set<String> aliases
    ) {
        if (slotNodes == null || slotNodes.isEmpty()) return "";

        // 1) Ajustar attrs recursivos (guarded) para refs a bindings del padre
        namespaceSlotAttributesRecursive(slotNodes, slotNsAlwaysParentId, parentCtx);

        // 2) Serializar blueprint preservando ":" y sin resolver valores
        StringBuilder sb = new StringBuilder();
        for (JrxNode n : slotNodes) {
            sb.append(serializeBlueprintNode(n, parentCtx, currentNs, aliases));
        }
        return sb.toString();
    }

    private void namespaceSlotAttributesRecursive(List<JrxNode> nodes, String ns, HtmlComponent ctx) {
        for (JrxNode n : nodes) {
            if (n instanceof ElementNode el) {
                // Copia defensiva
                Map<String, String> updated = new LinkedHashMap<>(el.attributes);

                for (Map.Entry<String, String> a : updated.entrySet()) {
                    String key = a.getKey();
                    String val = a.getValue() == null ? "" : a.getValue();

                    // :prop => prefix solo si pertenece al ctx y no es literal
                    if (key.startsWith(":")) {
                        if (!val.startsWith(ns) && !isLiteral(val)) {
                            String root = val.split("\\.")[0];
                            if (ctx.getRawBindings().containsKey(root)) {
                                el.attributes.put(key, ns + val);
                            }
                        }
                    }

                    // control attrs (si vienen en slot HTML ya convertidos)
                    if ("data-if".equals(key) || "data-else".equals(key) || "data-each".equals(key)) {
                        String root = val.split("[: ]")[0].split("\\.")[0];
                        if (ctx.getRawBindings().containsKey(root) && !val.contains(ns)) {
                            el.attributes.put(key, ns + val);
                        }
                    }

                    // Eventos @click / data-call => prefix solo si método existe en ctx
                    if (key.startsWith("@") || "data-call".equals(key)) {
                        String methodName = val.split("\\(")[0].trim();
                        if (hasCallable(ctx, methodName) && !val.startsWith(ns)) {
                            el.attributes.put(key, ns + val);
                        }
                    }
                }

                namespaceSlotAttributesRecursive(el.children, ns, ctx);
            } else if (n instanceof IfNode ifNode) {
                namespaceSlotAttributesRecursive(ifNode.trueBranch, ns, ctx);
                namespaceSlotAttributesRecursive(ifNode.falseBranch, ns, ctx);
            } else if (n instanceof EachNode eachNode) {
                namespaceSlotAttributesRecursive(eachNode.children, ns, ctx);
            }
        }
    }

 // ------------------------------------------------------------
 // Blueprint serializer (para contenido dentro de <template>)
 //  - Expande <slot/> aunque esté dentro de if/each
 //  - Renderiza ComponentNode (JForm/JInput/JButton/...) dentro de template
//     porque el runtime NO los expande al clonar templates
 //  - Resuelve {{...}} en attrs cuando sea posible (clave para @click="{{onRowClick}}")
 // ------------------------------------------------------------
 private String serializeBlueprintNode(JrxNode node, HtmlComponent ctx, String ns, Set<String> aliases) {
     if (node == null) return "";

     // Control blocks dentro de template
     if (node instanceof IfNode ifNode) {
         StringBuilder sb = new StringBuilder();
         renderIfBlueprint(ifNode, sb, ctx, ns, SESSION.get(), aliases);
         return sb.toString();
     }
     if (node instanceof EachNode eachNode) {
         StringBuilder sb = new StringBuilder();
         renderEachBlueprint(eachNode, sb, ctx, ns, SESSION.get(), aliases);
         return sb.toString();
     }

     // 🔥 IMPORTANTE: si hay componentes dentro del template (modal, forms, ui),
     // hay que renderizarlos en SSR, porque el runtime al clonar <template>
     // NO ejecuta expandComponentsAsync, solo hidrata eventos.
     if (node instanceof com.ciro.jreactive.ast.ComponentNode comp) {
         StringBuilder sb = new StringBuilder();
         renderComponent(comp, sb, ctx, ns, SESSION.get(), aliases);
         return sb.toString();
     }

     // Text
     if (node instanceof com.ciro.jreactive.ast.TextNode txt) {
         String t = (txt.text == null) ? "" : txt.text;
         return namespaceString(t, ns, ctx, aliases);
     }

     // Element
     if (node instanceof ElementNode el) {

         // 🔥 Slot dentro de template (ej: JTable tiene <slot/> dentro de {{#each}})
         if ("slot".equalsIgnoreCase(el.tagName)) {
             String slotHtml = ctx._getSlotHtml();
             if (slotHtml != null && !slotHtml.isBlank()) {
                 try {
                     List<JrxNode> slotNodes = JrxParser.parse(slotHtml);
                     StringBuilder sb = new StringBuilder();
                     for (JrxNode sn : slotNodes) {
                         sb.append(serializeBlueprintNode(sn, ctx, ns, aliases));
                     }
                     return sb.toString();
                 } catch (Exception ex) {
                     // fallback: mete el HTML crudo
                     return slotHtml;
                 }
             }
             // Si no hay slotHtml, renderiza children del <slot> (por si acaso)
             StringBuilder sb = new StringBuilder();
             for (JrxNode c : el.children) {
                 sb.append(serializeBlueprintNode(c, ctx, ns, aliases));
             }
             return sb.toString();
         }

         StringBuilder sb = new StringBuilder();
         sb.append("<").append(el.tagName);

         for (Map.Entry<String, String> a : el.attributes.entrySet()) {
             String k = a.getKey();
             String v = (a.getValue() == null) ? "" : a.getValue();

             // name="field" debe namespaciarse aunque NO tenga {{...}}
             if ("name".equals(k) && !v.contains("{{")) {
                 String root = v.split("\\.")[0];
                 if (ctx.getRawBindings().containsKey(root) && ns != null && !ns.isEmpty() && !v.startsWith(ns)) {
                     v = ns + v;
                 }
             }

             // data-if/data-else/data-each manuales (si aparecen dentro del slot/template)
             if ("data-if".equals(k) || "data-else".equals(k)) {
                 v = namespaceExpression(v, ns, ctx);
             } else if ("data-each".equals(k)) {
                 String[] parts = v.split(":");
                 if (parts.length > 0) {
                     String listExpr = namespaceExpression(parts[0], ns, ctx);
                     v = listExpr + (parts.length > 1 ? ":" + parts[1] : "");
                 }
             }

             // Namespace + intento de resolución estática de {{...}} (CLAVE para @click="{{onRowClick}}")
             if (v.contains("{{")) {
                 v = namespaceString(v, ns, ctx, aliases);

                 // Intentar resolver TODO si ya está disponible en bindings
                 String maybe = resolveAllMustachesIfPossible(v, ctx, ns);
                 if (maybe != null) v = maybe;
             }

             // Limpieza de props estilo :required, :disabled dentro de template
             // (en template no queremos attrs raros que “ensucien” el HTML)
             if (k.startsWith(":")) {
                 k = k.substring(1);
             }

             // Reescritura de eventos dentro de template (solo si ya NO tiene {{}})
             if ((k.startsWith("@") || "data-call".equals(k)) && (v != null && !v.contains("{{"))) {
                 v = rewriteEvent(v, ns, ctx);
             }

             sb.append(" ").append(k);
             if (v != null && !v.isEmpty()) {
                 sb.append("=\"").append(escapeAttr(v)).append("\"");
             }
         }

         if (el.isSelfClosing) {
             sb.append("/>");
             return sb.toString();
         }

         sb.append(">");
         for (JrxNode c : el.children) {
             sb.append(serializeBlueprintNode(c, ctx, ns, aliases));
         }
         sb.append("</").append(el.tagName).append(">");
         return sb.toString();
     }

     // fallback
     StringBuilder sb = new StringBuilder();
     node.renderRaw(sb);
     return sb.toString();
 }

    // ------------------------------------------------------------
    // Namespacing helpers
    // ------------------------------------------------------------
    private String namespaceString(String input, String ns, HtmlComponent ctx, Set<String> aliases) {
        if (input == null || input.isEmpty() || ns == null || ns.isEmpty()) return input;

        Matcher m = VAR_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();

        while (m.find()) {
            String varName = m.group(1);
            String root = varName.split("\\.")[0];

            boolean isLocalAlias = aliases != null && aliases.contains(root);
            boolean isKnownBinding = ctx.getRawBindings().containsKey(root);

            if (isLocalAlias || varName.equals("this") || varName.startsWith(ns) || !isKnownBinding) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement("{{" + ns + varName + "}}"));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String namespaceExpression(String expr, String ns, HtmlComponent ctx) {
        if (expr == null) return null;
        if (ns == null || ns.isEmpty()) return expr;
        String clean = expr.trim();
        if (clean.equals("this")) return clean;

        if (clean.startsWith("!")) {
            return "!" + namespaceExpression(clean.substring(1), ns, ctx);
        }

        String root = clean.split("\\.")[0];
        if (ctx.getRawBindings().containsKey(root) && !clean.startsWith(ns)) {
            return ns + clean;
        }
        return clean;
    }

    private String rewriteEvent(String callSignature, String ns, HtmlComponent ctx) {
        if (callSignature == null) return null;
        if (ns == null || ns.isEmpty()) return callSignature;
        if (callSignature.contains("#")) return callSignature;

        Matcher m = EVENT_PATTERN.matcher(callSignature.trim());
        if (!m.matches()) return callSignature;

        String methodName = m.group(1);
        String args = m.group(2);

        if (!hasCallable(ctx, methodName)) {
            return callSignature;
        }

        String newMethod = ns.substring(0, ns.length() - 1) + "." + methodName;

        if (args == null || args.isBlank()) return newMethod + "()";

        String newArgs = Arrays.stream(args.split(","))
                .map(String::trim)
                .filter(a -> !a.isEmpty())
                .map(arg -> {
                    if (arg.startsWith("'") || arg.startsWith("\"") || Character.isDigit(arg.charAt(0)) ||
                        arg.equals("true") || arg.equals("false")) return arg;

                    String root = arg.split("\\.")[0];
                    return ctx.getRawBindings().containsKey(root) ? ns + arg : arg;
                })
                .collect(Collectors.joining(", "));

        return newMethod + "(" + newArgs + ")";
    }

    private boolean hasCallable(HtmlComponent ctx, String method) {
        if (ctx.getCallableMethods() != null && ctx.getCallableMethods().containsKey(method)) return true;

        // fallback reflection (equivalente al Jsoup engine)
        return Arrays.stream(ctx.getClass().getMethods())
                .anyMatch(m -> m.getName().equals(method) &&
                        m.isAnnotationPresent(com.ciro.jreactive.annotations.Call.class));
    }

    // ------------------------------------------------------------
    // Static injection helpers (atributos con {{...}})
    // ------------------------------------------------------------
    private String resolveAllMustachesIfPossible(String templ, HtmlComponent ctx, String ns) {
        String current = templ;
        Matcher m = VAR_PATTERN.matcher(templ);
        boolean changed = false;

        while (m.find()) {
            String token = m.group(0);
            String fullKey = m.group(1);

            String localKey = fullKey;
            if (ns != null && !ns.isEmpty() && localKey.startsWith(ns)) {
                localKey = localKey.substring(ns.length());
            }
            if (localKey.startsWith("this.")) localKey = localKey.substring(5);

            ReactiveVar<?> var = ctx.getRawBindings().get(localKey);
            if (var != null) {
                Object v = var.get();
                String strVal = v == null ? "" : HtmlEscaper.escape(String.valueOf(v));
                current = current.replace(token, strVal);
                changed = true;
            }
        }

        // Si aún quedan {{...}} => no resolvemos (dejar para JS/store)
        if (current.contains("{{")) return null;
        return changed ? current : current;
    }

    // ------------------------------------------------------------
    // Boolean evaluator (|| && ! () ) equivalente a Jsoup
    // ------------------------------------------------------------
    private boolean evalBoolExpr(String expr, HtmlComponent ctx, String ns) {
        if (expr == null) return false;
        String s = expr.trim();
        if (s.isEmpty()) return false;

        // Si expr es un path (ej: page_x.disabled) intentamos resolverlo
        // Sólo si NO contiene operadores.
        if (!s.contains("||") && !s.contains("&&") && !s.contains("(") && !s.contains(")") && !s.contains("!")) {
            // limpiar namespace y "this."
            String local = s;
            if (ns != null && !ns.isEmpty() && local.startsWith(ns)) local = local.substring(ns.length());
            if (local.startsWith("this.")) local = local.substring(5);

            ReactiveVar<?> v = ctx.getRawBindings().get(local);
            if (v != null) {
                Object o = v.get();
                if (o instanceof Boolean b) return b;
                if (o instanceof Number n) return n.doubleValue() != 0;
                if (o instanceof String st) return !st.isEmpty() && !st.equalsIgnoreCase("false");
                return o != null;
            }
        }

        // Normalizar literales
        s = s.replace("\"", "").replace("'", "").trim().toLowerCase();

        if (s.equals("true")) return true;
        if (s.equals("false")) return false;

        final String in = s;

        class P {
            int i = 0;

            void skipWs() {
                while (i < in.length() && Character.isWhitespace(in.charAt(i))) i++;
            }

            boolean match(String tok) {
                skipWs();
                if (in.startsWith(tok, i)) {
                    i += tok.length();
                    return true;
                }
                return false;
            }

            boolean parseExpr() { return parseOr(); }

            boolean parseOr() {
                boolean v = parseAnd();
                while (true) {
                    if (match("||")) v = v || parseAnd();
                    else break;
                }
                return v;
            }

            boolean parseAnd() {
                boolean v = parseUnary();
                while (true) {
                    if (match("&&")) v = v && parseUnary();
                    else break;
                }
                return v;
            }

            boolean parseUnary() {
                if (match("!")) return !parseUnary();
                return parsePrimary();
            }

            boolean parsePrimary() {
                skipWs();

                if (match("(")) {
                    boolean v = parseExpr();
                    match(")");
                    return v;
                }

                if (in.startsWith("true", i)) { i += 4; return true; }
                if (in.startsWith("false", i)) { i += 5; return false; }

                // desconocido => false
                while (i < in.length()) {
                    char c = in.charAt(i);
                    if (Character.isWhitespace(c) || c == ')' || c == '|' || c == '&') break;
                    i++;
                }
                return false;
            }
        }

        try {
            return new P().parseExpr();
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------
    // Scope helpers: SOLO root elements
    // ------------------------------------------------------------
    private void addScopeToRootElements(List<JrxNode> nodes, String scopeId) {
        if (nodes == null || scopeId == null || scopeId.isBlank()) return;
        for (JrxNode n : nodes) {
            if (n instanceof ElementNode el && !(n instanceof com.ciro.jreactive.ast.ComponentNode) && !"slot".equalsIgnoreCase(el.tagName)) {
                String cls = el.attributes.getOrDefault("class", "");
                if (cls.isEmpty()) el.attributes.put("class", scopeId);
                else if (!cls.contains(scopeId)) el.attributes.put("class", cls + " " + scopeId);
            }
        }
    }

    // ------------------------------------------------------------
    // Token @Stateless (igual a Jsoup)
    // ------------------------------------------------------------
    private String injectStatelessToken(String html, Map<String, ReactiveVar<?>> all, String rootIdOrNull) {
        try {
            Map<String, Object> tokenState = new HashMap<>();
            all.forEach((k, v) -> tokenState.put(k, v.get()));

            String token = JrxStateToken.encode(tokenState);
            String rawJson = JrxStateToken.toJson(tokenState);

            return "<meta name=\"jrx-state\" content=\"" + token + "\">\n" +
                   "<script>window.__JRX_STATE__ = " + rawJson +
                   "; window.__JRX_ROOT_ID__ = " + (rootIdOrNull == null ? "null" : ("'" + rootIdOrNull + "'")) +
                   ";</script>\n" + html;
        } catch (Exception e) {
            e.printStackTrace();
            return html;
        }
    }

    // ------------------------------------------------------------
    // Resources once (equivalente a Jsoup engine)
    // ------------------------------------------------------------
    private String getResourcesOnce(HtmlComponent comp, Set<String> emitted) {
        String key = comp.getClass().getName();
        if (emitted.contains(key)) return "";
        emitted.add(key);
        return comp._getBundledResources();
    }

    // ------------------------------------------------------------
    // Bindings recursive (equivalente a Jsoup)
    // ------------------------------------------------------------
    private void collectBindingsRecursive(HtmlComponent comp, Map<String, ReactiveVar<?>> all) {
        String prefix = comp.getId() + ".";
        comp.getRawBindings().forEach((k, v) -> all.put(prefix + k, v));

        for (HtmlComponent child : comp._children()) {
            collectBindingsRecursive(child, all);
        }
    }

    // ------------------------------------------------------------
    // Utilities
    // ------------------------------------------------------------
    private static boolean isBooleanAttr(String k) {
        return switch (k) {
            case "disabled", "checked", "required", "selected", "readonly", "multiple", "hidden" -> true;
            default -> false;
        };
    }

    private boolean isLiteral(String s) {
        if (s == null) return true;
        return s.matches("true|false|-?\\d+(\\.\\d+)?|'.*'|\".*\"");
    }

    private void writeAttr(StringBuilder out, String key, String val) {
        out.append(" ").append(key);
        if (val != null && !val.isEmpty()) {
            out.append("=\"").append(escapeAttr(val)).append("\"");
        }
    }

    private String escapeAttr(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String escapeComment(String s) {
        if (s == null) return "";
        // Evita cerrar el comentario accidentalmente
        return s.replace("--", "—");
    }
}