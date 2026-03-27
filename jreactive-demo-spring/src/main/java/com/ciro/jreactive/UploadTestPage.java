package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.router.Layout;
import com.ciro.jreactive.router.Route;


@Route(path = "/uploadTest")
public class UploadTestPage extends AppPage {

	@State
    public JrxFile state = new JrxFile("", "", "", 0, "");
    
    @State
    int clickCount = 0;

    

    @Override
    protected String template() {
        return """
            <h1>Subir archivo reactivo</h1>
            <input type="file" name="file" @change="handleUpload(file)" @click="trackClick(file)"/>
            
            <p>Último archivo: <strong>{{state.name}}</strong></p>
            <p>Tamaño: {{state.size}} bytes</p>
            <p>clicks: {{clickCount}}</p>
            <p>Ruta Temporal Segura: <code style="color: green;">{{state.tempPath}}</code></p>
            """;
    }

    @Call
    public void handleUpload(JrxFile file) {
        if (file == null) return;
        
        // ¡Magia! El developer ya lo tiene listo para guardar
        System.out.println("Archivo listo en: " + file.tempPath());
        this.state = file;
    }
    
    @Call
    public void trackClick(JrxFile file) {
        // el parámetro "file" aquí te puede llegar null la primera vez,
        // pero no lo necesitamos; solo contamos clicks
        clickCount++;
    }
}
