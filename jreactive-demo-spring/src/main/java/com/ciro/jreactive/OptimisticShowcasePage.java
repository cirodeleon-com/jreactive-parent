package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.router.Route;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

@Route(path = "/optimistic")
public class OptimisticShowcasePage extends AppPage {

    // --- ESTADO --- 
    @State public boolean liked = false;
    @State public int likesCount = 42;
    
    @State public List<String> tasks = new ArrayList<>(List.of("Aprobar PR de JReactive", "Lanzar v1.0", "Celebrar"));
    
    @State public double total = 0.0;
    @State public int items = 0;

    // --- MÉTODOS DEL SERVIDOR (TODOS CON 1 SEGUNDO DE LAG) ---

    @Call
    public void toggleLike() {
        simularLag();
        this.liked = !this.liked;
        this.likesCount += this.liked ? 1 : -1;
    }

    @Call
    public void deleteTask(String task) {
        simularLag();
        this.tasks.remove(task);
    }

    @Call
    public void buyItem() {
        simularLag();
        this.total += 150.50;
        this.items++;
    }

    @Call
    public void bombAction() {
        simularLag();
        // Simulamos que se cayó la base de datos
        throw new RuntimeException("🔥 Se cayó el servidor en medio de la acción.");
    }

    private void simularLag() {
        try { Thread.sleep(1000); } catch (Exception e) {}
    }

    // --- PLANTILLA HTML ---
    @Override
    protected String template() {
        return """
            <div class="showcase-container">
                <h1>⚡ Optimistic UI Showcase</h1>
                <p>Todos los botones tienen <strong>1 segundo de retraso en el servidor</strong>. Observa cómo el cliente reacciona en 0ms.</p>

                <div class="card">
                    <h3>1. Truco Visual: CSS Class</h3>
                    <button class="btn-heart {{#if liked}}is-liked{{/if}}" 
                            @click="toggleLike()" 
                            jrx-optimistic-class="is-liked">
                        {{#if liked}}❤️{{else}}🤍{{/if}} Me gusta ({{likesCount}})
                    </button>
                    <small>Se pondrá rojo al instante, pero el contador tardará 1s.</small>
                </div>

                <div class="card">
                    <h3>2. Truco Visual: Ocultar Elementos</h3>
                    <div>
                        {{#each tasks as task}}
                            <div class="task-row">
                                <span>{{task}}</span>
                                <button class="btn-delete" 
                                        @click="deleteTask(task)" 
                                        jrx-optimistic-hide=".task-row">
                                    Borrar
                                </button>
                            </div>
                        {{/each}}
                    </div>
                </div>

                <div class="card">
                    <h3>3. Mutación de Estado (In-line Seguro)</h3>
                    <p>Carrito: <strong>{{items}}</strong> items | Total: <strong>${{total}}</strong></p>
                    <button @click="buyItem()" 
                            data-optimistic="items:+1; total:+150.50"
                            style="padding: 10px; background: #007bff; color: white; border: none; border-radius: 4px;">
                        🛒 Comprar ($150.50)
                    </button>
                </div>

                <div class="card" style="border-color: #dc3545; background: #fff8f8;">
                    <h3 style="color: #dc3545;">4. La prueba de fuego (Fallo del Servidor)</h3>
                    <p>Si la petición falla, JReactive debe revertir la ilusión óptica.</p>
                    <button @click="bombAction()" 
                            jrx-optimistic-class="is-loading"
                            data-optimistic="likesCount:+1000"
                            style="padding: 10px; background: #dc3545; color: white; border: none; border-radius: 4px;">
                        💣 Acción Peligrosa (+1000 likes falsos)
                    </button>
                    <small>Le suma 1000 likes al instante y se pone gris. Cuando el servidor lance la Excepción, volverá a la normalidad.</small>
                </div>

            </div>
        """;
    }
}