package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.annotations.Client;
import com.ciro.jreactive.router.Route;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;

@Component
@Route(path = "/chart")
@Client
public class ChartTestPage extends AppPage {

    // El estado que vigilará JReactive
    @State
    public List<Integer> salesData = List.of(12, 19, 3, 5, 2, 3);

    @Call
    public void randomizeData() {
        Random r = new Random();
        this.salesData = List.of(
            r.nextInt(20), r.nextInt(20), r.nextInt(20), 
            r.nextInt(20), r.nextInt(20), r.nextInt(20)
        );
    }

    @Override
    protected String template() {
        return """ 
            <div class="chart-container">
                <h1>📈 Interoperabilidad JReactive + Chart.js</h1>
                <p>El servidor envía un array Java, el DOM lo retiene como atributo y JS lo pinta.</p>

                <button @click="randomizeData()" class="btn-random">
                    Cambiar Datos desde Java
                </button>

                <div class="canvas-wrapper">
                    <canvas id="myChart" 
                            jrx-ignore
                            data-chart="{{salesData}}"
                            client:mount="window.ChartDemo.init(this)"
                            client:update="window.ChartDemo.update(this)"
                            client:unmount="window.ChartDemo.destroy(this)">
                    </canvas>
                </div>
                
                <p style="margin-top: 20px;"><a data-router href="/">⬅️ Volver al Inicio</a></p>
            </div>
        """;
    }
}