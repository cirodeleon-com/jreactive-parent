package com.ciro.jreactive;

import org.springframework.stereotype.Component;
import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.router.Route;

@Component
@Route(path = "/optimistic-test")
public class OptimisticTestPage extends AppPage {

    // --- ESTADO PRINCIPAL ---
    @State public int likes = 145;
    @State public boolean hasLiked = false;

    // --- ESTADO DERIVADO (La vista es tonta) ---
    @State public String heartIcon = "🤍";
    @State public String heartColor = "#ccc";

    // --- MÉTODOS DEL SERVIDOR ---

    @Call
    public void toggleLikeNormal() {
        simularLatencia(1000);
        procesarLike();
    }

    @Call
    public void toggleLikeOptimistic() {
        simularLatencia(1000);
        procesarLike();
    }

    @Call
    public void toggleLikeError() {
        simularLatencia(1000);
        throw new RuntimeException("Error 500: Fallo en la Base de Datos al guardar el Like.");
    }

    // --- LÓGICA INTERNA ---
    private void procesarLike() {
        if (hasLiked) {
            likes--;
            hasLiked = false;
            heartIcon = "🤍";
            heartColor = "#ccc";
        } else {
            likes++;
            hasLiked = true;
            heartIcon = "❤️";
            heartColor = "#e25555";
        }
    }

    private void simularLatencia(int ms) {
        try { Thread.sleep(ms); } catch (Exception e) {}
    }

    // --- VISTA ---
    @Override
    protected String template() {
        // language=html
        return """
            <div style="padding: 40px; font-family: system-ui, sans-serif; max-width: 600px; margin: 0 auto;">
                <h1>⚡ Laboratorio de Optimistic UI</h1>
                <p style="color: #666;">
                    Observa la diferencia de latencia percibida. Todos los botones tienen 
                    <strong>1 segundo de retraso programado</strong> en el servidor.
                </p>

                <div style="background: #f8f9fa; padding: 30px; border-radius: 12px; border: 1px solid #e0e0e0; text-align: center; margin-bottom: 30px;">
                    <h1 style="font-size: 4rem; margin: 0; display: flex; justify-content: center; align-items: center; gap: 10px;">
                        
                        <span style="color: {{heartColor}}; transition: color 0.2s;">
                            {{heartIcon}}
                        </span>
                        
                        <span>{{likes}}</span>
                    </h1>
                    <p>Me gustas en este post</p>
                </div>

                <div style="display: flex; flex-direction: column; gap: 15px;">
                    
                    <button @click="toggleLikeNormal()" 
                            style="padding: 15px; font-size: 1.1em; cursor: pointer; border-radius: 8px; border: 1px solid #ccc; background: white;">
                        🐢 <strong>Normal:</strong> Espera al servidor (1s de lag)
                    </button>

                    <button @click="toggleLikeOptimistic()" 
                            data-optimistic="
                                state.hasLiked = !state.hasLiked; 
                                state.likes += state.hasLiked ? 1 : -1; 
                                state.heartIcon = state.hasLiked ? '❤️' : '🤍'; 
                                state.heartColor = state.hasLiked ? '#e25555' : '#ccc';
                            "
                            style="padding: 15px; font-size: 1.1em; cursor: pointer; border-radius: 8px; border: none; background: #007bff; color: white;">
                        ⚡ <strong>Optimista:</strong> Reacción en 0ms (Manda petición en 2do plano)
                    </button>

                    <button @click="toggleLikeError()" 
                            data-optimistic="
                                state.hasLiked = !state.hasLiked; 
                                state.likes += state.hasLiked ? 1 : -1; 
                                state.heartIcon = state.hasLiked ? '❤️' : '🤍'; 
                                state.heartColor = state.hasLiked ? '#e25555' : '#ccc';
                            "
                            style="padding: 15px; font-size: 1.1em; cursor: pointer; border-radius: 8px; border: none; background: #dc3545; color: white;">
                        💣 <strong>Rollback:</strong> Optimista (0ms) + Error del Servidor
                    </button>

                </div>
            </div>
        """;
    }
}