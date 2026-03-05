package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.annotations.Client;
import com.ciro.jreactive.annotations.StatefulRam;
import com.ciro.jreactive.router.Route;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
@Route(path = "/mapa")
@Client
@StatefulRam
public class MapTestPage extends AppPage {

    // Record para representar las coordenadas
    public static class MarkerData implements Serializable {
        public double lat;
        public double lng;
        public String title;
        
        public MarkerData() {}
        public MarkerData(double lat, double lng, String title) {
            this.lat = lat; this.lng = lng; this.title = title;
        }
    }

    @State
    public List<MarkerData> markers = new ArrayList<>(List.of(
        new MarkerData(4.6097, -74.0817, "Bogotá"),
        new MarkerData(6.2442, -75.5812, "Medellín")
    ));

    @Call
    public void addRandomDriver() {
        Random r = new Random();
        // Genera coordenadas cerca de Colombia
        double lat = 2.0 + (r.nextDouble() * 8.0);
        double lng = -76.0 + (r.nextDouble() * 4.0);
        markers.add(new MarkerData(lat, lng, "Conductor " + (markers.size() + 1)));
    }

    @Call
    public void clearMap() {
        markers.clear();
    }

    @Override
    protected String template() {
        return """
            <div class="map-demo">
                <h1>🚚 Flota en Tiempo Real (Leaflet)</h1>
                <div style="margin-bottom: 15px; display: flex; gap: 10px;">
                    <button @click="addRandomDriver()" class="btn-map">📍 Añadir Conductor</button>
                    <button @click="clearMap()" class="btn-map btn-danger">🗑️ Limpiar Mapa</button>
                </div>
                
                <div class="map-wrapper">
                    <div id="leafletMap" 
                         jrx-ignore
                         data-markers="{{markers}}"
                         client:mount="window.LeafletDemo.init(this)"
                         client:update="window.LeafletDemo.update(this)"
                         client:unmount="window.LeafletDemo.destroy(this)">
                    </div>
                </div>
                
                <p style="margin-top: 20px;"><a data-router href="/">⬅️ Volver</a></p>
            </div>
        """;
    }
}