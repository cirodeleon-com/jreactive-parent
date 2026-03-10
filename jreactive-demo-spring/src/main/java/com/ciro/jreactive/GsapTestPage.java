package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.annotations.Client;
import com.ciro.jreactive.annotations.StatefulRam;
import com.ciro.jreactive.router.Route;
import org.springframework.stereotype.Component;

import java.util.Random;


@Route(path = "/gsap")
@Client
@StatefulRam
public class GsapTestPage extends AppPage {

    // El estado (La Verdad Funcional reside en Java)
    @State public int boxX = 0;
    @State public int boxY = 0;
    @State public int boxRotation = 0;
    @State public String boxColor = "#88ce02"; // Verde GSAP por defecto

    @Call
    public void randomizePosition() {
        Random r = new Random();
        // Coordenadas aleatorias entre -200 y 200
        this.boxX = r.nextInt(400) - 200; 
        this.boxY = r.nextInt(400) - 200;
        // Rotación aleatoria entre 0 y 360 grados
        this.boxRotation = r.nextInt(360);
        
        String[] colors = {"#88ce02", "#e91e63", "#00bcd4", "#ff9800", "#9c27b0", "#3f51b5"};
        this.boxColor = colors[r.nextInt(colors.length)];
    }

    @Override
    protected String template() {
        return """
            <div class="gsap-container">
                <h1>🎬 Interoperabilidad JReactive + GSAP</h1>
                <p>Java calcula las coordenadas y el color. GSAP anima la transición en el cliente.</p>

                <button @click="randomizePosition()" class="btn-gsap">
                    ✨ Animar (Backend)
                </button>

                <div class="stage">
                    <div id="miCaja" 
                         class="anim-box"
                         jrx-ignore
                         data-x="{{boxX}}"
                         data-y="{{boxY}}"
                         data-rot="{{boxRotation}}"
                         data-color="{{boxColor}}"
                         client:mount="window.GsapDemo.init(this)"
                         client:update="window.GsapDemo.update(this)"
                         client:unmount="window.GsapDemo.destroy(this)">
                        JRX
                    </div>
                </div>
                
                <p style="margin-top: 40px; z-index: 10;"><a data-router href="/">⬅️ Volver al Inicio</a></p>
            </div>
        """;
    }
}