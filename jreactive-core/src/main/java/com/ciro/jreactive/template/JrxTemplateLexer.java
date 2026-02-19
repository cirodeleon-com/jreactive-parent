package com.ciro.jreactive.template;

import java.util.ArrayList;
import java.util.List;

/**
 * Un Lexer O(N) de un solo paso (Single-pass).
 * Es Pareto-minimalista: Reemplaza las frágiles Expresiones Regulares
 * por una máquina de estados determinista que ignora las llaves '}}'
 * si están dentro de comillas (Strings literales).
 */
public class JrxTemplateLexer {

    public enum TokenType {
        TEXT,         // HTML puro: <div>Hola</div>
        VAR,          // Variable: {{ user.name }}
        BLOCK_OPEN,   // Apertura: {{#if cond}} o {{#each list as item}}
        BLOCK_CLOSE,  // Cierre: {{/if}} o {{/each}}
        BLOCK_ELSE    // Alternativa: {{else}}
    }

    public record Token(TokenType type, String content) {}

    public static List<Token> lex(String template) {
        List<Token> tokens = new ArrayList<>();
        if (template == null || template.isEmpty()) return tokens;

        int i = 0;
        int len = template.length();
        StringBuilder buffer = new StringBuilder();

        while (i < len) {
            // Buscamos apertura de llaves '{{'
            if (i + 1 < len && template.charAt(i) == '{' && template.charAt(i + 1) == '{') {
                
                // 1. Todo lo acumulado antes de '{{' es HTML puro (TEXT)
                if (!buffer.isEmpty()) {
                    tokens.add(new Token(TokenType.TEXT, buffer.toString()));
                    buffer.setLength(0);
                }
                
                i += 2; // Saltamos el '{{'
                
                // 2. Máquina de estados para leer DENTRO del '{{ ... }}'
                boolean inSingleQuote = false;
                boolean inDoubleQuote = false;
                
                while (i < len) {
                    char c = template.charAt(i);
                    
                    // Manejo de caracteres de escape dentro de strings (ej: \")
                    if (c == '\\' && (inSingleQuote || inDoubleQuote)) {
                        buffer.append(c);
                        i++;
                        if (i < len) {
                            buffer.append(template.charAt(i));
                            i++;
                        }
                        continue;
                    }
                    
                    // Detectar entrada/salida de strings
                    if (c == '\'' && !inDoubleQuote) inSingleQuote = !inSingleQuote;
                    else if (c == '"' && !inSingleQuote) inDoubleQuote = !inDoubleQuote;
                    
                    // Si encontramos '}}' y NO estamos dentro de un string, cerramos el bloque
                    else if (!inSingleQuote && !inDoubleQuote && c == '}' && i + 1 < len && template.charAt(i + 1) == '}') {
                        i += 2; // Saltamos el '}}'
                        break;  // Salimos del estado interno
                    }
                    
                    buffer.append(c);
                    i++;
                }

                // 3. Clasificar el contenido que estaba dentro de las llaves
                String content = buffer.toString().trim();
                buffer.setLength(0);

                if (content.startsWith("#")) {
                    tokens.add(new Token(TokenType.BLOCK_OPEN, content.substring(1).trim()));
                } else if (content.startsWith("/")) {
                    tokens.add(new Token(TokenType.BLOCK_CLOSE, content.substring(1).trim()));
                } else if (content.equals("else")) {
                    tokens.add(new Token(TokenType.BLOCK_ELSE, ""));
                } else {
                    tokens.add(new Token(TokenType.VAR, content));
                }

            } else {
                // Estado normal: acumulando HTML
                buffer.append(template.charAt(i));
                i++;
            }
        }

        // Si quedó algo en el buffer al final, es texto
        if (!buffer.isEmpty()) {
            tokens.add(new Token(TokenType.TEXT, buffer.toString()));
        }

        return tokens;
    }
}