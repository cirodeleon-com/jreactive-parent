package com.ciro.jreactive;

import com.ciro.jreactive.router.UrlVariable;
import com.ciro.jreactive.router.Route;
import static com.ciro.jreactive.Type.$;


@Route(path="/users/{id}")
public class UserPage extends AppPage {

    @UrlVariable("id")
    @State String userId;

    @State public String name = "";
    
    @Override
    protected void onInit() {
        // Simulamos una búsqueda en DB basada en el parámetro URL
        if ("10".equals(userId)) {
            this.name="Ciro (Admin)";
        } else {
            this.name="Visitante " + userId;
        }
    }

    @Override
    protected String template() {
        return """
          <div>
            <h2>User {{name}}</h2>
            <p>ID: {{userId}}</p>
          </div>
        """;
    }

}
