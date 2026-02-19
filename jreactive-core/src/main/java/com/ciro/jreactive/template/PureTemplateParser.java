package com.ciro.jreactive.template;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class PureTemplateParser {

    public TemplateNode parse(String src) {
        if (src == null || src.isEmpty()) return new ContainerNode();
        
        ContainerNode root = new ContainerNode();
        Deque<ContainerNode> stack = new ArrayDeque<>();
        stack.push(root);

        // 1. Extraemos los tokens de forma segura con el nuevo Lexer
        List<JrxTemplateLexer.Token> tokens = JrxTemplateLexer.lex(src);

        // 2. Armamos el árbol usando un Switch limpio
        for (JrxTemplateLexer.Token token : tokens) {
            switch (token.type()) {
                case TEXT -> 
                    stack.peek().addChild(new TextNode(token.content()));
                    
                case VAR -> 
                    stack.peek().addChild(new VarNode(token.content()));
                    
                case BLOCK_OPEN -> {
                    ContainerNode node = createBlock(token.content());
                    stack.peek().addChild(node);
                    stack.push(node); // Entramos al nuevo contexto
                }
                
                case BLOCK_CLOSE -> {
                    if (stack.size() > 1) {
                        stack.pop(); // Salimos del contexto
                    }
                }
                
                case BLOCK_ELSE -> 
                    stack.peek().enableElse();
            }
        }
        
        return root;
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