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

    // Regex ultra-rápida solo para separar los atributos pre-limpiados por el Lexer
    private static final Pattern ATTR_PATTERN = Pattern.compile("([:\\w@.-]+)(?:\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+)))?");

    public static List<JrxNode> parse(String html) {
        List<JrxLexer.Token> tokens = JrxLexer.lex(html);
        return parseTokens(tokens);
    }

    public static List<JrxNode> parseTokens(List<JrxLexer.Token> tokens) {
        List<JrxNode> rootNodes = new ArrayList<>();
        Stack<JrxNode> stack = new Stack<>();

        // Helper: Decide a quién pertenece el nuevo nodo (al Root, a un Div, a un If, etc.)
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
                    String cmd = t.name(); // ej: "if cond" o "each list as item"
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
                        ifNode.inElse = true; // Cambiamos la ruta de inserción
                    }
                }
                
                case BLOCK_CLOSE -> {
                    // Cerrar un if o un each
                    if (!stack.isEmpty()) {
                        JrxNode top = stack.peek();
                        if (top instanceof IfNode || top instanceof EachNode) {
                            stack.pop();
                        }
                    }
                }
                
                case TAG_OPEN -> {
                    ElementNode el;
                    // Detectamos si es un Componente (Empieza con mayúscula)
                    if (Character.isUpperCase(t.name().charAt(0))) {
                        el = new ComponentNode(t.name(), t.selfClosing());
                    } else {
                        el = new ElementNode(t.name(), t.selfClosing());
                    }

                    // Extraer los atributos
                    if (!t.content().isEmpty()) {
                        Matcher m = ATTR_PATTERN.matcher(t.content());
                        while (m.find()) {
                            String key = m.group(1);
                            // Tomamos el valor de comillas dobles, simples, o sin comillas
                            String val = m.group(2) != null ? m.group(2) : (m.group(3) != null ? m.group(3) : m.group(4));
                            if (val == null) val = ""; // Para casos booleanos como "disabled"
                            el.attributes.put(key, val);
                        }
                    }

                    addNode.accept(el);
                    // Si NO es de auto-cierre (<input/>), lo metemos a la pila para recibir hijos
                    if (!t.selfClosing()) {
                        stack.push(el);
                    }
                }
                
                case TAG_CLOSE -> {
                    // Buscar la etiqueta de apertura correspondiente en la pila
                    int popCount = 0;
                    for (int i = stack.size() - 1; i >= 0; i--) {
                        JrxNode node = stack.get(i);
                        if (node instanceof ElementNode el && el.tagName.equals(t.name())) {
                            popCount = stack.size() - i;
                            break;
                        }
                    }
                    // Sacar todo hasta cerrar la etiqueta (Tolerancia a HTML mal formado)
                    for (int i = 0; i < popCount; i++) {
                        stack.pop();
                    }
                }
            }
        }

        return rootNodes;
    }
}