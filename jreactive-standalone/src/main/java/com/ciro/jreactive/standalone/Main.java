package com.ciro.jreactive.standalone;

import com.ciro.jreactive.HtmlComponent;
import com.ciro.jreactive.State;
import com.ciro.jreactive.annotations.Call;

public class Main {

    public static void main(String[] args) {
        // 1. Configurar puerto
        int port = 8080;
        
        // 2. Instanciar servidor
        JReactiveServer server = new JReactiveServer(port);

        // 3. Registrar rutas manuales
        // Ruta raíz "/" -> Crea una nueva instancia de CounterPage
        server.addRoute("/", CounterPage::new);
        
        // Puedes agregar más:
        // server.addRoute("/otra", OtraPagina::new);

        // 4. Arrancar
        System.out.println("⏳ Iniciando servidor JReactive Standalone...");
        server.start();
    }

    /**
     * Componente de prueba integrado para verificar que todo funciona.
     * Tiene estado (@State) y eventos (@Call) para probar WebSocket.
     */
    public static class CounterPage extends HtmlComponent {
        
        @State
        public int count = 0;

        @Call
        public void increment() {
            count++;
            // Al ser un primitivo int, el framework detecta el cambio automáticamente
            // al terminar la ejecución si usamos la lógica Smart.
            // O explícitamente:
            updateState("count"); 
        }

        @Override
        protected String template() {
            return """
                <div style="font-family: sans-serif; text-align: center; padding: 50px;">
                    <h1>🚀 JReactive Standalone</h1>
                    <p>Corriendo sobre <strong>Undertow</strong> (Sin Spring Boot)</p>
                    
                    <div style="border: 1px solid #ccc; padding: 20px; display: inline-block; border-radius: 8px;">
                        <h2>Contador: {{count}}</h2>
                        <button 
                            @click="increment()"
                            style="padding: 10px 20px; font-size: 1.2em; cursor: pointer; background: #007bff; color: white; border: none; border-radius: 4px;"
                        >
                            ¡Click me!
                        </button>
                    </div>
                </div>
            """;
        }
    }
}