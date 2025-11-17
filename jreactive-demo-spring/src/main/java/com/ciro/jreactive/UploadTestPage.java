package com.ciro.jreactive;

import org.springframework.stereotype.Component;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.router.Route;

@Component
@Route(path = "/uploadTest")
public class UploadTestPage extends HtmlComponent {

    @State
    JrxFile state = new JrxFile("","",0,"");

    

    @Override
    protected String template() {
        return """
            <h1>Subir archivo</h1>

           
              <input type="file"
                   name="file"
                   @change="handleUpload(file)" />
              
            

            <p>Último archivo: {{state.name}}</p>
            <p>Tamaño: {{state.size}}</p>
            <p>Content: {{state.base64}}</p>
            """;
    }

    @Call
    public void handleUpload(JrxFile file) {
        if (file == null) {
            return;
        }
        state = new JrxFile(file.name(),file.contentType(),file.size(),file.base64());
        updateState("state");  // dispara el re-render de {{state.*}}
    }
}
