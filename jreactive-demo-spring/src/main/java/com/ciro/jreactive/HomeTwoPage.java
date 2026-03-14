package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.router.Route;

@Route(path = "/two")
public class HomeTwoPage extends AppPage {

    // 1. Un estado para ver que los botones sí hacen algo
    @State 
    public String status = "";

    // 2. Los métodos que los botones van a llamar
    @Call
    public void cancelar() {
        this.status = "✅ Uff... Operación cancelada. Los datos de producción están a salvo.";
    }

    @Call
    public void borrar() {
        this.status = "🔥 ¡BOOOM! Disco duro formateado (mentira, es solo una prueba).";
    }

    @Override
    protected String template() {
        return """
          <div class="page" style="padding: 20px;">
            <h1>🧩 Prueba de Named Slots</h1>
            
            <JCard title="Aviso Importante">
                
                <p>¿Estás seguro de que deseas formatear el disco duro de producción? Esta acción no se puede deshacer y la Verdad Funcional será eliminada.</p>
                
                {{#if status}}
                    <div style="margin-top: 15px; padding: 10px; background: #fff3cd; color: #856404; border-radius: 4px; font-weight: bold;">
                        {{status}}
                    </div>
                {{/if}}

                <template slot="footer">
                    <button @click="cancelar()" style="padding: 8px 15px; cursor: pointer; border: 1px solid #ccc; border-radius: 4px; background: white;">Cancelar</button>
                    <button @click="borrar()" style="padding: 8px 15px; cursor: pointer; background: #dc3545; color: white; border: none; border-radius: 4px;">💣 Formatear</button>
                </template>
                
            </JCard>
            
            <br><br>
            <a href="/" data-router>⬅️ Regresar al inicio</a>
          </div>
          """;
    }
}