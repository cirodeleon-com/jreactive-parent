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
            
            <JFileUpload 
                    label="Sube tu documento pesado" 
                    field="state" 
                    onChange="handleUpload(state)" 
                    onClick="trackClick(state)" 
                />
            
            <p>Último archivo: <strong>{{state.name}}</strong></p>
            <p>Tamaño: {{state.formattedSize}}</p>
            
            <p>clicks: {{clickCount}}</p>
            <p>Ruta Temporal Segura: <code style="color: green;">{{state.tempPath}}</code></p>
            """;
    }

    @Call
    public void handleUpload(JrxFile file) {
        if (file == null) return;
        
        System.out.println("✅ Archivo recibido en Java: " + file.tempPath());
        this.state = file;
    }
    
    @Call
    public void trackClick(JrxFile file) {
        // el parámetro "file" aquí te puede llegar null la primera vez,
        // pero no lo necesitamos; solo contamos clicks
        clickCount++;
    }
}
