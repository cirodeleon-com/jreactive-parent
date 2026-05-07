package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.router.Route;

@Route(path = "/shoelace-two")
public class ShoelacePageTwo extends AppPage {

    @State
    public boolean cargando = false;

    @State
    public String textoBoton = "Ejecutar Acción";

    @Call
    public void ejecutar() {
        this.cargando = true;
        this.textoBoton = "Procesando...";
        System.out.println("✅ [JAVA] ¡Shoelace conectado por WebSocket!");
    }

    @Override
    protected String template() {
        return """
            <div style="padding: 40px; font-family: sans-serif;">
                <h1>Shoelace + JReactive 🚀</h1>

                <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/@shoelace-style/shoelace@2.15.0/cdn/themes/light.css" />
                <script type="module" src="https://cdn.jsdelivr.net/npm/@shoelace-style/shoelace@2.15.0/cdn/shoelace-autoloader.js"></script>

                <div style="margin-top: 20px;">
                    <sl-button
                        variant="primary"
                        :loading="cargando"
                        @click="ejecutar()"
                    >
                        {{textoBoton}}
                    </sl-button>
                </div>
            </div>
        """;
    }
}