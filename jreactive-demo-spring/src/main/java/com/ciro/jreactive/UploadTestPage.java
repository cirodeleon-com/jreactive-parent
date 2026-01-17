package com.ciro.jreactive;

import org.springframework.stereotype.Component;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.router.Layout;
import com.ciro.jreactive.router.Route;

@Component
@Route(path = "/uploadTest")
public class UploadTestPage extends AppPage {

    @State
    JrxFile state = new JrxFile("","",0,"");
    
    @State
    int clickCount = 0;

    

    @Override
    protected String template() {
        return """
            <h1>Subir archivo</h1>

           
              <input type="file"
                   name="file"
                   @change="handleUpload(file)"
                   @click="trackClick(file)" />
              
            

            <p>Último archivo: {{state.name}}</p>
            <p>Tamaño: {{state.size}}</p>
            <p>Content: {{state.base64}}</p>
            <p><strong>Clicks en el input:</strong> {{clickCount}}</p>
            """;
    }

    @Call
    public void handleUpload(JrxFile file) {
        if (file == null) {
            return;
        }
        
        
        state = new JrxFile(file.name(),file.contentType(),file.size(),file.base64());
        
    }
    
    @Call
    public void trackClick(JrxFile file) {
        // el parámetro "file" aquí te puede llegar null la primera vez,
        // pero no lo necesitamos; solo contamos clicks
        clickCount++;
        updateState("clickCount");
    }
}
