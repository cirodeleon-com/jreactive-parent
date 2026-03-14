package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.router.Route;

@Route(path = "/modal-test")
public class ModalTestPage extends AppPage {

    @State public String status = "Esperando acción...";
    @State public boolean isModalOpen = false; // 👈 NUEVO

    @Call
    public void openModal() {
        this.isModalOpen = true;
    }

    @Call
    public void closeModal() {
        this.isModalOpen = false;
    }

    @Call
    public void confirmarAccion() {
        this.status = "✅ Acción confirmada desde el Modal!";
        this.isModalOpen = false; // 👈 Cerramos reactivamente
    }

    @Override
    protected String template() {
        return """
            <div style="padding: 50px; text-align: center;">
                <h1>🧪 Prueba de Modal Reactivo</h1>
                <p>Estado actual: <strong>{{status}}</strong></p>

                <button @click="openModal()" style="padding: 10px 20px;">
                    🔓 Abrir Modal
                </button>

                <JModal title="Confirmación Requerida" :visible="isModalOpen" onClose="closeModal()">
                    <p>¿Estás seguro de que quieres aplicar la Verdad Funcional?</p>
                    <div style="margin-top: 20px; display: flex; gap: 10px; justify-content: flex-end;">
                        <button @click="closeModal()">Cancelar</button>
                        <button @click="confirmarAccion()" style="background: #007bff; color: white; border: none; padding: 5px 15px; border-radius: 4px;">
                            Sí, confirmar
                        </button>
                    </div>
                </JModal>
            </div>
        """;
    }
}