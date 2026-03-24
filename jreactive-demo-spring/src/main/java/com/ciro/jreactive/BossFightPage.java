package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.annotations.StatefulRam;
import com.ciro.jreactive.router.Route;
import java.util.ArrayList;
import java.util.List;
import java.io.Serializable;
import java.util.UUID;

@Route(path = "/boss-fight")
@StatefulRam
public class BossFightPage extends AppPage {

    // El DTO de la opción
    public static class Opcion implements Serializable {
        public String id;
        public String texto;
        public int votos;

        public Opcion() {}
        public Opcion(String texto, int votos) {
            this.id = UUID.randomUUID().toString().substring(0, 5);
            this.texto = texto;
            this.votos = votos;
        }
    }

    // 1. Estado Complejo (Lista de Objetos)
    @State 
    public List<Opcion> opciones = new ArrayList<>(List.of(
        new Opcion("Microservicios", 15),
        new Opcion("Monolito Modular", 42)
    ));

    // 2. Estado Simple y Foco de Input
    @State public String nuevoComentario = "";
    @State public List<String> comentarios = new ArrayList<>();

    // --- ACCIONES ---

    @Call
    public void votar(String id) {
        // Para ver si realmente llega
    	
    	try { Thread.sleep(500); } catch (Exception e) {}
    	
    	if(Math.random()>0.5) {
    		throw new RuntimeException("Error crítico: No se pudo conectar a la BD");
    	}else {
    		System.out.println("Votando por: " + id); 
        for (int i = 0; i < opciones.size(); i++) {
            Opcion o = opciones.get(i);
            if (o.id.equals(id)) {
                o.votos++;
                // 🔥 LA MAGIA: Forzamos el trigger del Delta usando .set()
                opciones.set(i, o); 
                break;
            }
        }
    	}
        
    }

    @Call
    public void enviarComentario() {
        if (nuevoComentario != null && !nuevoComentario.isBlank()) {
            comentarios.add(nuevoComentario);
            nuevoComentario = ""; // Limpiamos el input desde el servidor
        }
    }

    @Override
    protected String template() {
        return """
            <div style="padding: 30px; font-family: sans-serif; max-width: 600px; margin: auto;">
                <h1>🔥 Boss Fight: Encuesta + Chat</h1>
                <p style="color: #666;">Prueba de estrés para el Morphing del DOM y el Foco.</p>

                <JCard title="¿Cuál es la mejor arquitectura?">
                    
                    <div style="margin-bottom: 20px;">
                        {{#each opciones as opt}}
                            <div style="display: flex; justify-content: space-between; padding: 10px; border-bottom: 1px solid #eee;">
                                <span>{{opt.texto}} <strong id="votos-{{opt.id}}">({{opt.votos}} votos)</strong></span>
                                
                                <button @click="votar('{{opt.id}}')" 
                                        onclick="window.BossFight.optimistaVoto('{{opt.id}}', this)"
                                        onmousedown="event.preventDefault()"
                                        style="padding: 5px 15px; background: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer;">
                                    Votar
                                </button>
                            </div>
                        {{/each}}
                    </div>

                    <div style="background: #f8f9fa; padding: 15px; border-radius: 8px;">
                        <h3>Chat en vivo ({{comentarios.size}} msjs)</h3>
                        
                        <div style="height: 100px; overflow-y: auto; margin-bottom: 10px; border: 1px solid #ddd; padding: 5px; background: white;">
                            {{#each comentarios as msg}}
                                <div style="margin-bottom: 5px;">💬 {{msg}}</div>
                            {{/each}}
                        </div>

                        <div style="display: flex; gap: 10px;">
                            <input type="text" 
                                   name="nuevoComentario" 
                                   placeholder="Escribe y no sueltes el foco..." 
                                   style="flex: 1; padding: 8px;" 
                                   @input.debounce.300ms="enviarComentario()" />
                                   
                            <button @click="enviarComentario()" style="padding: 8px 15px; background: #28a745; color: white; border: none; border-radius: 4px;">
                                Enviar
                            </button>
                        </div>
                    </div>
                </JCard>
                <p style="margin-top: 20px;"><a data-router href="/">⬅️ Volver al Inicio</a></p>
            </div>
        """;
    }
}