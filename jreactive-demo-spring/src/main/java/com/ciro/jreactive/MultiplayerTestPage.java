package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.annotations.Client;
import com.ciro.jreactive.annotations.Shared;
import com.ciro.jreactive.annotations.StatefulRam;
import com.ciro.jreactive.router.Route;
import com.ciro.jreactive.router.UrlParam;
import com.ciro.jreactive.router.UrlVariable; // 👈 ¡No olvides esta importación!

import java.util.ArrayList;
import java.util.List;

// 1️⃣ Añadimos {salaId} a la ruta para que sea dinámica
@Route(path = "/multiplayer")
//@Client
@StatefulRam
public class MultiplayerTestPage extends AppPage {

    // 2️⃣ Capturamos el parámetro de la URL y lo volvemos un @State
    //@UrlVariable("salaId")
    @UrlParam("sala")
    @State
    public String sala;

    // 3️⃣ Usamos {{salaId}} para que cada URL tenga su propio espacio aislado en Redis
    @Shared("demo-sala-chat-{{sala}}")
    public List<String> mensajes = new ArrayList<>();

    @Shared("demo-sala-counter-{{sala}}")
    public int clicsGlobales = 0;

    // 👤 ESTADO LOCAL (Solo vive en la memoria de este usuario específico)
    @State
    public String miMensaje = "";

    // --- ACCIONES ---

    @Call
    public void sumarClic() {
        clicsGlobales++; 
    }

    @Call
    public void enviarMensaje() {
        if (miMensaje != null && !miMensaje.isBlank()) {
            mensajes.add(miMensaje); 
            miMensaje = ""; 
        }
    }

    @Call
    public void resetearSala() {
        mensajes.clear();
        clicsGlobales = 0;
    }

    // --- VISTA ---
    @Override
    protected String template() {
        return """
            <div style="padding: 30px; font-family: system-ui, sans-serif; max-width: 600px; margin: 0 auto;">
                
                <h1 style="color: #2c3e50;">🌐 Sala JReactive: <span style="color: #2196f3;">{{sala}}</span></h1>
                
                <p style="color: #666;">
                    Abre esta página en <strong>dos navegadores distintos</strong>. 
                    Luego, cambia la URL a <a data-router href="/multiplayer/privada">/multiplayer/privada</a> 
                    para ver cómo el estado se aísla automáticamente.
                </p>

                <div style="background: #e3f2fd; padding: 20px; border-radius: 8px; text-align: center; margin-bottom: 20px; border: 2px dashed #2196f3;">
                    <h2>Clics Globales: <span style="color: #d32f2f; font-size: 1.5em;">{{clicsGlobales}}</span></h2>
                    <button @click="sumarClic()" 
                            style="padding: 10px 20px; font-size: 1.2em; background: #2196f3; color: white; border: none; border-radius: 6px; cursor: pointer;">
                        👇 Sumar Clic
                    </button>
                </div>

                <JCard title="💬 Chat Global en Vivo">
                    <div style="height: 250px; overflow-y: auto; background: #f8f9fa; border: 1px solid #ddd; padding: 10px; margin-bottom: 15px; border-radius: 4px;">
                        {{#if !mensajes.size}}
                            <p style="color: #999; text-align: center; margin-top: 100px;">La sala está vacía. ¡Rompe el hielo!</p>
                        {{/if}}
                        
                        {{#each mensajes as msg}}
                            <div style="background: white; padding: 8px 12px; margin-bottom: 8px; border-radius: 15px; border: 1px solid #eee; width: fit-content; max-width: 80%;">
                                {{msg}}
                            </div>
                        {{/each}}
                    </div>

                    <div style="display: flex; gap: 10px;">
                        <input type="text" 
                               name="miMensaje" 
                               @input.debounce.300ms="enviarMensaje()"
                               placeholder="Escribe un mensaje..." 
                               style="flex: 1; padding: 10px; border: 1px solid #ccc; border-radius: 4px;" />
                               
                        <button @click="enviarMensaje()" style="padding: 10px 20px; background: #28a745; color: white; border: none; border-radius: 4px; cursor: pointer;">
                            Enviar
                        </button>
                    </div>
                </JCard>
                
                <div style="margin-top: 20px; text-align: right;">
                    <button @click="resetearSala()" style="background: transparent; color: #dc3545; border: 1px solid #dc3545; padding: 5px 10px; border-radius: 4px; cursor: pointer;">
                        💣 Resetear Sala
                    </button>
                </div>

                <p style="margin-top: 30px;"><a data-router href="/">⬅️ Volver al Inicio</a></p>
            </div>
        """;
    }
}