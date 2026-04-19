package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.annotations.Defer;
import com.ciro.jreactive.annotations.StatefulRam;
import com.ciro.jreactive.router.Route;

import java.util.ArrayList;
import java.util.List;

@Route(path = "/defer-test")
@StatefulRam
public class DeferTestPage extends AppPage {

    // 1. Estado en null. El SSR escupirá el bloque {{else}} inmediatamente.
    @State
    public List<String> logs; 

    // 2. JReactive lanza esto en un Virtual Thread apenas se monta el componente.
    @Defer("logs")
    public List<String> cargarDatosPesados() {
        try {
            // Simulamos 2.5 segundos de latencia de una Base de Datos en la nube
            Thread.sleep(2500); 
        } catch (InterruptedException e) {}
        
        // Devolvemos un ArrayList puro. JReactive lo convertirá a SmartList.
        return new ArrayList<>(List.of("📦 Sistema Iniciado", "🔍 Escaneo Completo"));
    }

    // 3. Prueba de fuego para el SmartList
    @Call
    public void agregarLog() {
        // Como 'logs' ya fue convertido en SmartList por el @Defer,
        // esto viajará como un Delta {op: "ADD"} hiper ligero.
        logs.add("⚡ Nuevo evento a las: " + System.currentTimeMillis());
    }

    // 🔥 NUEVO: Método para probar la recarga asíncrona
    @Call
    public void recargarDatos() {
        // Vaciamos la lista para forzar el estado "Pending" (mostrar spinner)
        this.logs = null; 
        
        // Invocamos el Helper que creaste en HtmlComponent
        // Esto dispara un nuevo Hilo Virtual y no bloquea este @Call
        this.reloadDeferred("logs");
    }

    @Override
    protected String template() {
        return """
            <div style="padding: 40px; font-family: system-ui; max-width: 600px; margin: auto;">
                <h1>⏳ Prueba de Deferred State</h1>
                <p style="color: #666;">Carga asíncrona sin bloquear el primer renderizado.</p>
                
                {{#if logs}}
                    <div style="background: #e6fced; padding: 20px; border-radius: 8px; border: 1px solid #b7ebc7; animation: fadeIn 0.5s;">
                        <h3 style="margin-top:0; color: #28a745;">✅ Datos Cargados</h3>
                        <ul>
                            {{#each logs as log}}
                                <li>{{log}}</li>
                            {{/each}}
                        </ul>
                        
                        <div style="display: flex; gap: 10px; margin-top: 15px;">
                            <button @click="agregarLog()" style="padding: 8px 16px; background: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer;">
                                ➕ Añadir Log (Delta O(1))
                            </button>
                            
                            <button @click="recargarDatos()" style="padding: 8px 16px; background: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer;">
                                🔄 Recargar (Virtual Thread)
                            </button>
                        </div>
                    </div>
                {{else}}
                    <div style="background: #f8f9fa; padding: 40px; border-radius: 8px; text-align: center; border: 2px dashed #ccc;">
                        <h3 style="color: #666; margin:0;">🔄 Consultando a la Base de Datos...</h3>
                        <p style="color: #999; font-size: 14px;">(Este HTML se sirvió en 0ms. El Hilo Virtual está trabajando)</p>
                    </div>
                {{/if}}
                
                <p style="margin-top: 30px;"><a data-router href="/">⬅️ Volver al Inicio</a></p>
                
                <style>
                    @keyframes fadeIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
                </style>
            </div>
        """;
    }
}