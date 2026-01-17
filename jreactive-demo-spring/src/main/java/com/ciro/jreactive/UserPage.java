package com.ciro.jreactive;

import org.springframework.stereotype.Component;

import com.ciro.jreactive.router.Param;
import com.ciro.jreactive.router.Route;
import static com.ciro.jreactive.Type.$;

@Component
@Route(path="/users/{id}")
public class UserPage extends AppPage {

    @Param("id")
    @Bind String userId;

    @Bind public Type<String> name = $("");
    
    @Override
    protected void onInit() {
        // Simulamos una búsqueda en DB basada en el parámetro URL
        if ("10".equals(userId)) {
            this.name.set("Ciro (Admin)");
        } else {
            this.name.set("Visitante " + userId);
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
