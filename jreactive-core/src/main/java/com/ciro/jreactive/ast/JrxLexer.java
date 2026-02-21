package com.ciro.jreactive.ast;

import java.util.ArrayList;
import java.util.List;

/**
 * Lexer O(N) Pareto-minimalista para JReactive.
 * Diseñado para leer HTML y tags de plantilla ({{...}}) en una sola pasada,
 * siendo 100% inmune a símbolos especiales dentro de strings/atributos.
 */
public class JrxLexer {

    public enum TokenType {
        TEXT,
        BLOCK_OPEN,
        BLOCK_CLOSE,
        BLOCK_ELSE,
        TAG_OPEN,
        TAG_CLOSE
    }

    public record Token(TokenType type, String name, String content, boolean selfClosing) {}

    public static List<Token> lex(String input) {
        List<Token> tokens = new ArrayList<>();
        if (input == null || input.isEmpty()) return tokens;

        int i = 0;
        int len = input.length();
        StringBuilder textBuffer = new StringBuilder();

        while (i < len) {
            // ==============================================================
            // 1. Detección de llaves de plantilla: {{ ... }}
            // ==============================================================
            if (i + 1 < len && input.charAt(i) == '{' && input.charAt(i + 1) == '{') {
                i += 2; // saltar {{
                
                StringBuilder varBuffer = new StringBuilder();
                boolean inSingleQuote = false;
                boolean inDoubleQuote = false;

                while (i < len) {
                    char c = input.charAt(i);
                    // Proteger comillas escapadas
                    if (c == '\\' && (inSingleQuote || inDoubleQuote)) {
                        varBuffer.append(c);
                        i++;
                        if (i < len) varBuffer.append(input.charAt(i));
                    } else if (c == '\'' && !inDoubleQuote) {
                        inSingleQuote = !inSingleQuote;
                        varBuffer.append(c);
                    } else if (c == '"' && !inSingleQuote) {
                        inDoubleQuote = !inDoubleQuote;
                        varBuffer.append(c);
                    } 
                    // Detectar cierre de llaves solo si NO estamos dentro de un string
                    else if (!inSingleQuote && !inDoubleQuote && c == '}' && i + 1 < len && input.charAt(i + 1) == '}') {
                        i += 2; // saltar }}
                        break;
                    } else {
                        varBuffer.append(c);
                    }
                    i++;
                }

                String content = varBuffer.toString().trim();
                if (content.startsWith("#")) {
                    flushText(tokens, textBuffer);
                    tokens.add(new Token(TokenType.BLOCK_OPEN, content.substring(1).trim(), "", false));
                } else if (content.startsWith("/")) {
                    flushText(tokens, textBuffer);
                    tokens.add(new Token(TokenType.BLOCK_CLOSE, content.substring(1).trim(), "", false));
                } else if (content.equals("else")) {
                    flushText(tokens, textBuffer);
                    tokens.add(new Token(TokenType.BLOCK_ELSE, "", "", false));
                } else {
                    // Es una variable {{var}}, la tratamos como texto para que TextNode haga la magia O(1)
                    textBuffer.append("{{").append(content).append("}}");
                }
                continue;
            }

            // ==============================================================
            // 2. Detección de etiquetas HTML: < ... >
            // ==============================================================
            if (input.charAt(i) == '<') {
                // Para ser tag, al '<' debe seguirle una letra o un '/'
                if (i + 1 < len && (Character.isLetter(input.charAt(i + 1)) || input.charAt(i + 1) == '/')) {
                    flushText(tokens, textBuffer);
                    
                    boolean isClose = input.charAt(i + 1) == '/';
                    int startIdx = isClose ? i + 2 : i + 1;
                    int j = startIdx;
                    
                    // Extraer el nombre del Tag (ej: div, JCard, slot)
                    StringBuilder tagNameB = new StringBuilder();
                    while (j < len && (Character.isLetterOrDigit(input.charAt(j)) || input.charAt(j) == '-')) {
                        tagNameB.append(input.charAt(j));
                        j++;
                    }
                    String tagName = tagNameB.toString();

                    // Extraer los atributos crudos hasta encontrar el '>'
                    StringBuilder attrBuffer = new StringBuilder();
                    boolean inSingleQuote = false;
                    boolean inDoubleQuote = false;
                    
                    while (j < len) {
                        char c = input.charAt(j);
                        
                        if (c == '"' && !inSingleQuote) inDoubleQuote = !inDoubleQuote;
                        else if (c == '\'' && !inDoubleQuote) inSingleQuote = !inSingleQuote;
                        // El '>' solo cierra la etiqueta si NO estamos dentro de un String
                        else if (c == '>' && !inSingleQuote && !inDoubleQuote) {
                            j++; // Incluir/saltar >
                            break;
                        }
                        attrBuffer.append(c);
                        j++;
                    }

                    String rawAttrs = attrBuffer.toString();
                    boolean selfClosing = false;
                    
                    if (isClose) {
                        tokens.add(new Token(TokenType.TAG_CLOSE, tagName, "", false));
                    } else {
                        // Detectar si el usuario escribió explícitamente "/>"
                        if (rawAttrs.endsWith("/")) {
                            selfClosing = true;
                            rawAttrs = rawAttrs.substring(0, rawAttrs.length() - 1);
                        }
                        // Autodetectar etiquetas HTML5 vacías (como <input>)
                        if (isHtml5Void(tagName)) {
                            selfClosing = true;
                        }
                        tokens.add(new Token(TokenType.TAG_OPEN, tagName, rawAttrs.trim(), selfClosing));
                    }
                    i = j;
                    continue; // Siguiente ciclo principal
                }
            }

            // ==============================================================
            // 3. Acumulación de Texto Plano
            // ==============================================================
            textBuffer.append(input.charAt(i));
            i++;
        }

        flushText(tokens, textBuffer);
        return tokens;
    }

    private static void flushText(List<Token> tokens, StringBuilder sb) {
        if (!sb.isEmpty()) {
            tokens.add(new Token(TokenType.TEXT, "", sb.toString(), false));
            sb.setLength(0);
        }
    }

    private static boolean isHtml5Void(String tag) {
        String t = tag.toLowerCase();
        return t.equals("br") || t.equals("hr") || t.equals("input") || t.equals("img") || 
               t.equals("link") || t.equals("meta") || t.equals("area") || t.equals("base") || 
               t.equals("col") || t.equals("embed") || t.equals("param") || t.equals("source") || 
               t.equals("track") || t.equals("wbr");
    }
}