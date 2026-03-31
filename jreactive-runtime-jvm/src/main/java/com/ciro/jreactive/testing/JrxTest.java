package com.ciro.jreactive.testing;

import com.ciro.jreactive.AstComponentEngine;
import com.ciro.jreactive.HtmlComponent;

import java.util.UUID;

/**
 * Utilidad minimalista para testing unitario de componentes JReactive.
 */
public final class JrxTest {

    // Inicializa el motor AOT/AST una sola vez para todo el suite de pruebas
    static {
        AstComponentEngine.installAsDefault();
    }

    private JrxTest() {}

    /**
     * Instancia un componente, le asigna un ID de prueba y ejecuta su ciclo
     * de vida inicial (onInit, onMount) dejándolo listo para interactuar.
     */
    public static <T extends HtmlComponent> T mount(Class<T> componentClass) {
        try {
            T comp = componentClass.getDeclaredConstructor().newInstance();
            
            // Asignamos un ID único y predecible para aislar el test
            comp.setId("test-" + componentClass.getSimpleName() + "-" + UUID.randomUUID().toString().substring(0, 5));
            
            // Disparamos el ciclo de vida inicial
            comp._initIfNeeded();
            comp._mountRecursive();
            
            // Forzamos el primer render para que arme el árbol interno y las referencias
            comp.render(); 
            
            return comp;
        } catch (Exception e) {
            throw new RuntimeException("❌ [JrxTest] Error montando el componente: " + componentClass.getSimpleName(), e);
        }
    }
}