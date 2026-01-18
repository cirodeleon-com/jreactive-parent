package com.ciro.jreactive;

import org.springframework.stereotype.Component;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.router.Route;

@Component
@Route(path = "/modal-test")
public class ModalTestPage extends AppPage {

    @State public String status = "Esperando acción...";

    @Call
    public void confirmarAccion() {
        this.status = "✅ Acción confirmada desde el Modal!";
        // Buscamos el modal por ref y lo cerramos desde el back
        findChild("miModal", JModal.class).close();
    }

    @Override
    protected String template() {
        return """
            <div style="padding: 50px; text-align: center;">
                <h1>🧪 Prueba de Modal Reactivo</h1>
                <p>Estado actual: <strong>{{status}}</strong></p>

                <button @click="miModal.open()" style="padding: 10px 20px;">
                    🔓 Abrir Modal
                </button>

                <JModal ref="miModal" title="Confirmación Requerida">
                    <p>¿Estás seguro de que quieres aplicar la Verdad Funcional?</p>
                    <div style="margin-top: 20px; display: flex; gap: 10px; justify-content: flex-end;">
                        <button @click="miModal.close()">Cancelar</button>
                        <button @click="confirmarAccion()" style="background: #007bff; color: white; border: none; padding: 5px 15px; border-radius: 4px;">
                            Sí, confirmar
                        </button>
                    </div>
                </JModal>
            </div>
        """;
    }
}