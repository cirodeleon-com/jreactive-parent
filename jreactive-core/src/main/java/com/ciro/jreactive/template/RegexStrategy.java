package com.ciro.jreactive.template;

import com.ciro.jreactive.HtmlComponent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexStrategy implements TemplateStrategy {

    private static final Pattern EACH_START = Pattern.compile("\\{\\{\\s*#each\\s+([\\w#.-]+)(?:\\s+as\\s+(\\w+))?\\s*}}");
    private static final String EACH_END_TOKEN = "{{/each}}";
    private static final Pattern IF_START = Pattern.compile("\\{\\{\\s*#if\\s+([^}]+)}}");
    private static final String IF_END_TOKEN = "{{/if}}";
    private static final String ELSE_TOKEN = "{{else}}";

    @Override
    public String process(String html, HtmlComponent ctx) {
        return processControlBlocks(html);
    }

    // Tu lógica original de AbstractComponentEngine (simplificada para este uso)
    private String processControlBlocks(String html) {
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
                if (endBlock == -1) { // Fallback si no cierra
                    sb.append(html, idxIf, mIf.end());
                    cursor = mIf.end();
                    continue;
                }
                String condition = mIf.group(1).trim();
                String body = html.substring(mIf.end(), endBlock);
                
                // Convertir a formato compatible con Jsoup XML
                sb.append("<template data-if=\"").append(condition).append("\">")
                  .append(processControlBlocks(body)) // Recursión
                  .append("</template>");
                  
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
                String body = html.substring(mEach.end(), endBlock);

                sb.append("<template data-each=\"").append(listExpr).append(":").append(alias).append("\">")
                  .append(processControlBlocks(body))
                  .append("</template>");

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
}