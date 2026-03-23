package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.router.Route;

@Route(path = "/shoelace")
public class ShoelacePage extends AppPage {

    @State 
    public String correo = "";

    @Call
    public void alEscribir(String valor) {
        this.correo = valor; // Actualizamos la Verdad Funcional
        System.out.println("Java recibió: " + valor);
    }

    @Override
    protected String template() {
        return """
            <div style="padding: 40px; font-family: sans-serif; max-width: 400px;">
                <h1>Shoelace + JReactive 🚀</h1>
                
                <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/@shoelace-style/shoelace@2.15.0/cdn/themes/light.css" />
                <script type="module" src="https://cdn.jsdelivr.net/npm/@shoelace-style/shoelace@2.15.0/cdn/shoelace-autoloader.js"></script>

                <div style="margin-top: 20px;">
                    <SlInput 
                        name="correo"
                        label="Correo Electrónico" 
                        placeholder="usuario@jreactive.com"
                        :value="correo" 
                        @sl-input="alEscribir(correo)" 
                    >
                        <span slot="prefix">✉️</span>
                    </SlInput>
                </div>

                <div style="margin-top: 20px; padding: 15px; background: #f0f0f0; border-radius: 8px;">
                    Estado en Java: <strong>{{correo}}</strong>
                </div>
            </div>
        """;
    }
}