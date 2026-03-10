package com.ciro.jreactive.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ensamblador O(N). Convierte los Tokens del Lexer en un Árbol (AST).
 */
public class JrxParser {

    private static final Pattern ATTR_PATTERN = Pattern.compile("([:\\w@.-]+)(?:\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+)))?");

    public static List<JrxNode> parse(String html) {
        List<JrxLexer.Token> tokens = JrxLexer.lex(html);
        return parseTokens(tokens);
    }

    public static List<JrxNode> parseTokens(List<JrxLexer.Token> tokens) {
        List<JrxNode> rootNodes = new ArrayList<>();
        Stack<JrxNode> stack = new Stack<>();

        Consumer<JrxNode> addNode = (node) -> {
            if (stack.isEmpty()) {
                rootNodes.add(node);
            } else {
                JrxNode parent = stack.peek();
                if (parent instanceof ElementNode el) {
                    el.children.add(node);
                } else if (parent instanceof EachNode each) {
                    each.children.add(node);
                } else if (parent instanceof IfNode ifNode) {
                    if (ifNode.inElse) {
                        ifNode.falseBranch.add(node);
                    } else {
                        ifNode.trueBranch.add(node);
                    }
                }
            }
        };

        for (JrxLexer.Token t : tokens) {
            switch (t.type()) {
                case TEXT -> addNode.accept(new TextNode(t.content()));
                
                case BLOCK_OPEN -> {
                    String cmd = t.name();
                    if (cmd.startsWith("if ")) {
                        IfNode ifNode = new IfNode(cmd.substring(3).trim());
                        addNode.accept(ifNode);
                        stack.push(ifNode);
                    } else if (cmd.startsWith("each ")) {
                        String clean = cmd.substring(5).trim();
                        String[] parts = clean.split(" as ");
                        String listExpr = parts[0].trim();
                        String alias = parts.length > 1 ? parts[1].trim() : "this";
                        
                        EachNode eachNode = new EachNode(listExpr, alias);
                        addNode.accept(eachNode);
                        stack.push(eachNode);
                    }
                }
                
                case BLOCK_ELSE -> {
                    if (!stack.isEmpty() && stack.peek() instanceof IfNode ifNode) {
                        ifNode.inElse = true;
                    }
                }
                
                case BLOCK_CLOSE -> {
                    // 🔥 FIX: Validación estricta. Si cerramos plantilla, el bloque superior NO puede ser HTML.
                    if (!stack.isEmpty()) {
                        JrxNode top = stack.peek();
                        if (top instanceof ElementNode el) {
                            throw new IllegalStateException("Etiqueta HTML sin cerrar: <" + el.tagName + "> antes del cierre {{/ ... }}");
                        } else if (top instanceof IfNode || top instanceof EachNode) {
                            stack.pop();
                        }
                    } else {
                        throw new IllegalStateException("Cierre {{/ ... }} inesperado. No hay ningún bloque abierto.");
                    }
                }
                
                case TAG_OPEN -> {
                    ElementNode el;
                    if (Character.isUpperCase(t.name().charAt(0))) {
                        el = new ComponentNode(t.name(), t.selfClosing());
                    } else {
                        el = new ElementNode(t.name(), t.selfClosing());
                    }

                    if (!t.content().isEmpty()) {
                        Matcher m = ATTR_PATTERN.matcher(t.content());
                        while (m.find()) {
                            String key = m.group(1);
                            String val = m.group(2) != null ? m.group(2) : (m.group(3) != null ? m.group(3) : m.group(4));
                            if (val == null) val = "";
                            el.attributes.put(key, val);
                        }
                    }

                    addNode.accept(el);
                    if (!t.selfClosing()) {
                        stack.push(el);
                    }
                }
                
                case TAG_CLOSE -> {
                    int popCount = 0;
                    boolean found = false;
                    for (int i = stack.size() - 1; i >= 0; i--) {
                        JrxNode node = stack.get(i);
                        if (node instanceof ElementNode el && el.tagName.equals(t.name())) {
                            popCount = stack.size() - i;
                            found = true;
                            break;
                        }
                        // 🔥 FIX: No permitimos que un cierre HTML pase por encima de un bloque de plantilla
                        if (node instanceof IfNode || node instanceof EachNode) {
                            String blockName = (node instanceof IfNode) ? "{{#if}}" : "{{#each}}";
                            throw new IllegalStateException("Cierre </" + t.name() + "> incorrecto. El bloque " + blockName + " no ha sido cerrado con {{/...}}");
                        }
                    }
                    
                    if (found) {
                        for (int i = 0; i < popCount; i++) {
                            stack.pop();
                        }
                    }
                }
            }
        }
        
        // Validación final de cosas que quedaron abiertas
        if (!stack.isEmpty()) {
            JrxNode unclosed = stack.peek();
            String errorMsg = "Etiqueta desconocida";
            
            if (unclosed instanceof IfNode) errorMsg = "{{#if ...}}";
            else if (unclosed instanceof EachNode) errorMsg = "{{#each ...}}";
            else if (unclosed instanceof ElementNode el) errorMsg = "<" + el.tagName + ">";
            
            throw new IllegalStateException("Falta cerrar: " + errorMsg);
        }

        return rootNodes;
    }
}