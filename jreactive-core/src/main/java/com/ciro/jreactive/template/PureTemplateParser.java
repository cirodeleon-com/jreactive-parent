package com.ciro.jreactive.template;

import java.util.ArrayDeque;
import java.util.Deque;

public class PureTemplateParser {
    private static final String OPEN = "{{";
    private static final String CLOSE = "}}";

    public TemplateNode parse(String src) {
        if (src == null) return new ContainerNode();
        ContainerNode root = new ContainerNode();
        Deque<ContainerNode> stack = new ArrayDeque<>();
        stack.push(root);

        int pos = 0;
        int len = src.length();

        while (pos < len) {
            int nextOpen = src.indexOf(OPEN, pos);
            if (nextOpen == -1) {
                stack.peek().addChild(new TextNode(src.substring(pos)));
                break;
            }
            if (nextOpen > pos) {
                stack.peek().addChild(new TextNode(src.substring(pos, nextOpen)));
            }

            int startTag = nextOpen + OPEN.length();
            int nextClose = src.indexOf(CLOSE, startTag);
            if (nextClose == -1) break;

            String content = src.substring(startTag, nextClose).trim();
            processTag(content, stack);
            pos = nextClose + CLOSE.length();
        }
        return root;
    }

    private void processTag(String content, Deque<ContainerNode> stack) {
        if (content.isEmpty()) return;
        
        if (content.startsWith("#")) {
            String cmd = content.substring(1).trim();
            ContainerNode node = createBlock(cmd);
            stack.peek().addChild(node);
            stack.push(node);
        } else if (content.startsWith("/")) {
            if (stack.size() > 1) stack.pop();
        } else if (content.equals("else")) {
            stack.peek().enableElse();
        } else {
            stack.peek().addChild(new VarNode(content));
        }
    }

    private ContainerNode createBlock(String command) {
        if (command.startsWith("if ")) return new IfNode(command.substring(3).trim());
        if (command.startsWith("each ")) {
            String clean = command.substring(5).trim();
            int asIdx = clean.indexOf(" as ");
            if (asIdx != -1) return new EachNode(clean.substring(0, asIdx).trim(), clean.substring(asIdx + 4).trim());
            return new EachNode(clean, "this");
        }
        return new ContainerNode();
    }
}