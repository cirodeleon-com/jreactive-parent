package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.router.Route;

@Route(path = "/toast")
public class ToastDemoPage extends AppPage {

    // El padre es el dueño del estado
    @State public boolean mostrarToast = false;
    @State public String mensajeToast = "";
    @State public String colorToast = "info";

    @Call
    public void lanzarExito() {
        this.mensajeToast = "¡Datos guardados correctamente! 🚀";
        this.colorToast = "success";
        this.mostrarToast = true; // ¡Detona la reactividad!
    }

    @Call
    public void lanzarError() {
        this.mensajeToast = "Error crítico en la base de datos 🚨";
        this.colorToast = "danger";
        this.mostrarToast = true;
    }

    @Call
    public void cerrarNotificacion() {
        // Este método será llamado por el botón (o el timer JS) del JToast
        this.mostrarToast = false;
    }

    @Override
    protected String template() {
        return """
            <div style="padding: 40px; font-family: system-ui;">
                <h1>🍞 El famoso Toast</h1>
                <p>Componente UI nativo con <strong>@Prop</strong> e inyección estática.</p>
                
                <div style="display: flex; gap: 15px; margin-top: 20px;">
                    <button @click="lanzarExito()" style="padding: 10px 20px; background: #4caf50; color: white; border: none; border-radius: 6px; cursor: pointer; font-weight: bold;">
                        Test Success
                    </button>
                    
                    <button @click="lanzarError()" style="padding: 10px 20px; background: #f44336; color: white; border: none; border-radius: 6px; cursor: pointer; font-weight: bold;">
                        Test Danger
                    </button>
                </div>

                <JToast 
                    :visible="mostrarToast" 
                    :message="mensajeToast" 
                    :variant="colorToast" 
                    onClose="cerrarNotificacion()" 
                />
                
                <p style="margin-top: 30px;"><a data-router href="/">⬅️ Volver</a></p>
            </div>
        """;
    }
}