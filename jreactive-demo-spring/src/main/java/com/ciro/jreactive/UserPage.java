package com.ciro.jreactive;

import org.springframework.stereotype.Component;

import com.ciro.jreactive.router.Param;
import com.ciro.jreactive.router.Route;
import static com.ciro.jreactive.Type.$;

@Component
@Route(path="/users/{id}")
public class UserPage extends HtmlComponent {

    @Param("id")
    @Bind String userId;

    @Bind public Type<String> name = $("juan");

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
