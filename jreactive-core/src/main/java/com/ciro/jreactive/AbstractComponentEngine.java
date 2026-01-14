package com.ciro.jreactive;

import com.ciro.jreactive.factory.ComponentFactory;
import com.ciro.jreactive.factory.DefaultComponentFactory;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractComponentEngine implements ComponentEngine.Strategy {

    private static final Pattern EACH_START = Pattern.compile("\\{\\{\\s*#each\\s+([\\w#.-]+)(?:\\s+as\\s+(\\w+))?\\s*}}");
    private static final String EACH_END_TOKEN = "{{/each}}";
    private static final Pattern IF_START = Pattern.compile("\\{\\{\\s*#if\\s+([^}]+)}}");
    private static final String IF_END_TOKEN = "{{/if}}";
    private static final String ELSE_TOKEN = "{{else}}";

    private static volatile ComponentFactory componentFactory = new DefaultComponentFactory();

    public static void setComponentFactory(ComponentFactory factory) {
        componentFactory = Objects.requireNonNull(factory, "componentFactory must not be null");
    }

    protected String processControlBlocks(String html) {
        StringBuilder sb = new StringBuilder();
        int cursor = 0;
        int len = html.length();

        while (cursor < len) {
            Matcher mIf = IF_START.matcher(html);
            boolean hasIf = mIf.find(cursor);
            Matcher mEach = EACH_START.matcher(html);
            boolean hasEach = mEach.find(cursor);

            if (!hasIf && !hasEach) {
                sb.append(html.substring(cursor));
                break;
            }

            int idxIf = hasIf ? mIf.start() : Integer.MAX_VALUE;
            int idxEach = hasEach ? mEach.start() : Integer.MAX_VALUE;

            if (idxIf < idxEach) {
                sb.append(html, cursor, idxIf);
                int endBlock = findMatchingEnd(html, mIf.end(), IF_START, IF_END_TOKEN);
                if (endBlock == -1) {
                    sb.append(html, idxIf, mIf.end());
                    cursor = mIf.end();
                    continue;
                }
                String condition = mIf.group(1).trim();
                String body = html.substring(mIf.end(), endBlock);
                int elseIdx = findElseIndex(body);

                if (elseIdx != -1) {
                    String truePart = processControlBlocks(body.substring(0, elseIdx));
                    String falsePart = processControlBlocks(body.substring(elseIdx + ELSE_TOKEN.length()));
                    sb.append("<template data-if=\"").append(condition).append("\">").append(truePart)
                      .append("</template><template data-else=\"").append(condition).append("\">").append(falsePart)
                      .append("</template>");
                } else {
                    sb.append("<template data-if=\"").append(condition).append("\">")
                      .append(processControlBlocks(body)).append("</template>");
                }
                cursor = endBlock + IF_END_TOKEN.length();
            } else {
                sb.append(html, cursor, idxEach);
                int endBlock = findMatchingEnd(html, mEach.end(), EACH_START, EACH_END_TOKEN);
                if (endBlock == -1) {
                    sb.append(html, idxEach, mEach.end());
                    cursor = mEach.end();
                    continue;
                }
                String listExpr = mEach.group(1).trim();
                String alias = (mEach.group(2) != null) ? mEach.group(2).trim() : "this";
                String processedBody = processControlBlocks(html.substring(mEach.end(), endBlock));
                sb.append("<template data-each=\"").append(listExpr).append(":").append(alias).append("\">")
                  .append(processedBody).append("</template>");
                cursor = endBlock + EACH_END_TOKEN.length();
            }
        }
        return sb.toString();
    }

    private int findMatchingEnd(String html, int startIdx, Pattern openPattern, String closeToken) {
        int depth = 1;
        int current = startIdx;
        while (current < html.length()) {
            Matcher mOpen = openPattern.matcher(html);
            int nextOpen = mOpen.find(current) ? mOpen.start() : -1;
            int nextClose = html.indexOf(closeToken, current);
            if (nextClose == -1) return -1;
            if (nextOpen != -1 && nextOpen < nextClose) {
                depth++;
                current = mOpen.end();
            } else {
                depth--;
                if (depth == 0) return nextClose;
                current = nextClose + closeToken.length();
            }
        }
        return -1;
    }

    private int findElseIndex(String body) {
        int dIf = 0, dEach = 0;
        Matcher m = Pattern.compile("(\\{\\{\\s*#if)|(\\{\\{\\s*/if)|(\\{\\{\\s*#each)|(\\{\\{\\s*/each)|(\\{\\{\\s*else\\s*}})", Pattern.CASE_INSENSITIVE).matcher(body);
        while (m.find()) {
            String t = m.group(0).toLowerCase();
            if (t.contains("#if")) dIf++;
            else if (t.contains("/if")) dIf--;
            else if (t.contains("#each")) dEach++;
            else if (t.contains("/each")) dEach--;
            else if (t.contains("else") && dIf == 0 && dEach == 0) return m.start();
        }
        return -1;
    }

    protected HtmlComponent createAndBindComponent(HtmlComponent parent, List<HtmlComponent> pool, Map<String, ReactiveVar<?>> globalBindings, String className, Map<String, String> attrs, String slotHtml) {
        String ref = attrs.get("ref");
        ViewLeaf leaf;
        if (ref != null) {
            leaf = pool.stream().filter(c -> ref.equals(c.getId())).map(c -> (ViewLeaf) c).findFirst()
                    .orElseGet(() -> { ViewLeaf f = newInstance(parent, className); f.setId(ref); return f; });
            pool.removeIf(c -> c == leaf);
        } else {
            Optional<HtmlComponent> re = pool.stream().filter(c -> c.getClass().getSimpleName().equals(className)).findFirst();
            if (re.isPresent()) { leaf = (ViewLeaf) re.get(); pool.remove(re.get()); }
            else leaf = newInstance(parent, className);
            leaf.setId(leaf.getId());
        }
        HtmlComponent hc = (HtmlComponent) leaf;
        if (slotHtml != null && !slotHtml.isBlank()) hc._setSlotHtml(slotHtml);
        parent._addChild(hc);
        
        Map<String, ReactiveVar<?>> childBinds = hc.selfBindings();
        attrs.forEach((k, v) -> {
            if (k.equals("ref")) return;
            boolean isB = k.startsWith(":");
            String prop = isB ? k.substring(1) : k;
            ReactiveVar<Object> target = (ReactiveVar<Object>) childBinds.get(prop);
            if (target != null) {
                if (isB) {
                    ReactiveVar<?> pRx = parent.selfBindings().get(v);
                    if (pRx == null) pRx = globalBindings.get(v);
                    if (pRx != null) { target.set(pRx.get()); pRx.onChange(target::set); }
                } else target.set(v);
            }
        });
        return hc;
    }

    private ViewLeaf newInstance(HtmlComponent ctx, String className) {
        try {
            Class<?> raw = Class.forName(ctx.getClass().getPackageName() + "." + className);
            return (ViewLeaf) componentFactory.create((Class<? extends ViewLeaf>) raw);
        } catch (Exception e) { throw new RuntimeException("Error instantiating " + className, e); }
    }

    protected void disposeUnused(List<HtmlComponent> pool) {
        if (pool == null) return;
        for (HtmlComponent z : pool) z._unmountRecursive();
        pool.clear();
    }
}