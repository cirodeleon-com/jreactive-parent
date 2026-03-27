package com.ciro.jreactive;

import org.springframework.stereotype.Component;
import com.ciro.jreactive.router.Route;


@Route(path = "/hook-test")
public class HookPage extends AppPage {

	@Override
    protected String template() {
        return """
            <div style="padding: 20px; font-family: sans-serif; max-width: 600px;">
                <h1>🪝 Prueba del Escudo Híbrido JS</h1>

                <h3>Prueba 1: Función Global (Modo Estricto CSP)</h3>
                <div 
                    client:mount="window.HookPage_mountLimpio" 
                    client:unmount="window.HookPage_unmountLimpio"
                    style="background: #f8f9fa; margin-bottom: 20px;"
                >
                    Esperando hook global...
                </div>
                
                <h3>Prueba 2: JS Inline crudo (Modo Flexible)</h3>
                <div 
                    client:mount="this.style.border='5px dashed red'; this.style.padding='20px'; this.innerHTML += '<br><strong>✅ JS Inline ejecutado usando new Function</strong>';"
                    style="background: #f8f9fa;"
                >
                    Esperando hook inline...
                </div>
                
                <hr style="margin-top: 30px;"/>
                <a href="/" data-router>⬅️ Volver</a>
            </div>
        """;
    }
}