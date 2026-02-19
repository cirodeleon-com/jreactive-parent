package com.ciro.jreactive;

import com.helger.css.ECSSVersion;
import com.helger.css.decl.*;
import com.helger.css.reader.CSSReader;
import com.helger.css.writer.CSSWriter;
import com.helger.css.writer.CSSWriterSettings;
import java.util.List;
import java.util.ArrayList;

public class CssScoper {

    /**
     * Parsea el CSS y prefija todos los selectores con la clase scopeId.
     * Soporta :host para referirse al propio elemento raíz.
     *
     * @param css El contenido CSS crudo
     * @param scopeId El nombre de la clase de aislamiento (ej: jrx-sc-Button)
     * @return CSS transformado y minificado
     */
    public static String scope(String css, String scopeId) {
        if (css == null || css.isBlank()) return "";

        // 1. Parsear CSS a un AST (Árbol de objetos)
        // Usamos CSS30 para soporte moderno
        CascadingStyleSheet aCSS = CSSReader.readFromString(css, ECSSVersion.CSS30);
        
        if (aCSS == null) {
            System.err.println("⚠️ JReactive: Error de sintaxis CSS en el componente. Se ignora el estilo.");
            return "";
        }

        // 2. Recorrer y modificar reglas
        scopeRules(aCSS.getAllRules(), scopeId);

        // 3. Escribir de nuevo a String (Minificado por defecto)
        CSSWriterSettings settings = new CSSWriterSettings(ECSSVersion.CSS30);
        settings.setOptimizedOutput(true); // Minificar
        
        try {
            return new CSSWriter(settings).getCSSAsString(aCSS);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private static void scopeRules(List<ICSSTopLevelRule> rules, String scopeId) {
        for (ICSSTopLevelRule rule : rules) {
            if (rule instanceof CSSStyleRule styleRule) {
                // Regla normal: button { ... }
                processStyleRule(styleRule, scopeId);
            } 
            else if (rule instanceof CSSMediaRule mediaRule) {
                // Recursión para @media screen { ... }
                scopeRules(mediaRule.getAllRules(), scopeId);
            }
            else if (rule instanceof CSSKeyframesRule) {
                // Los keyframes NO se scopean (son globales), o requieren lógica compleja.
                // Por ahora los dejamos tal cual para que las animaciones funcionen.
            }
        }
    }

    private static void processStyleRule(CSSStyleRule rule, String scopeId) {
        // 1. Hacemos una copia de los selectores originales para no iterar y borrar al mismo tiempo
        List<CSSSelector> originalSelectors = new ArrayList<>(rule.getAllSelectors());
        
        // 2. Limpiamos los selectores de la regla para rellenarla con los corregidos
        rule.removeAllSelectors();

        for (CSSSelector oldSelector : originalSelectors) {
            CSSSelector newSelector = new CSSSelector();
            
            if (oldSelector.getMemberCount() > 0) {
                ICSSSelectorMember first = oldSelector.getMemberAtIndex(0);
                
                // --- Caso 1: :host (Referencia al nodo raíz) ---
                // Se transforma de ":host" a ".scopeId"
                if (first instanceof CSSSelectorSimpleMember simple && ":host".equalsIgnoreCase(simple.getValue())) {
                    // Agregamos el scope
                    newSelector.addMember(new CSSSelectorSimpleMember("." + scopeId));
                    
                    // Copiamos el resto de los miembros (saltando el 0 que era :host)
                    for (int i = 1; i < oldSelector.getMemberCount(); i++) {
                        newSelector.addMember(oldSelector.getMemberAtIndex(i));
                    }
                } 
                // --- Caso 2: Selector Normal (Descendiente) ---
                // Se transforma de "div" a ".scopeId div"
                else {
                    // 1. Insertamos el scope
                    newSelector.addMember(new CSSSelectorSimpleMember("." + scopeId));
                    // 2. Insertamos un espacio (combinador descendiente)
                    newSelector.addMember(ECSSSelectorCombinator.BLANK);
                    
                    // 3. Copiamos TODOS los miembros originales
                    for (ICSSSelectorMember member : oldSelector.getAllMembers()) {
                        newSelector.addMember(member);
                    }
                }
            } else {
                // Si el selector estaba vacío (raro), lo dejamos igual
                for (ICSSSelectorMember member : oldSelector.getAllMembers()) {
                    newSelector.addMember(member);
                }
            }
            
            // 3. Añadimos el selector reconstruido a la regla
            rule.addSelector(newSelector);
        }
    }
}