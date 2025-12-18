package com.ciro.jreactive;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import static com.ciro.jreactive.Type.$;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.router.Route;

@Component
@Route(path = "/newStateTest")
public class NewStateTestPage extends HtmlComponent {

    // 1) @State SIN Type ni $()
    @State
    public PageState state = new PageState();

    // 2) Input normal con @Bind + Type (esto sí sigue con Type)
    @State
    public String newItem = "";

    public static class PageState {
        public String title = "Demo @State sin Type";
        public List<String> items = new ArrayList<>(
            List.of("Uno", "Dos", "Tres")
        );
    }

    @Call
    public void addItem(String value) {
        if (value == null || value.isBlank()) return;

        var copy = new ArrayList<>(state.items);
        copy.add(value);
        state.items = copy;

        newItem="";
    }

    @Call
    public void resetList() {
        state.items = new ArrayList<>();
    }

    @Call
    public void changeTitle(String title) {
        state.title = title;
    }

    @Override
    protected String template() {
        return """
        <fieldset style='border:1px solid #888;padding:8px;width:360px'>
          <legend>{{state.title}}</legend>

          <div>
            <strong>Total:</strong>
            {{state.items.size}}
          </div>

          <div style='margin:8px 0'>
            <input name="newItem" placeholder="Nuevo item">
            <button @click="addItem(newItem)">Agregar</button>
            <button @click="resetList()">Limpiar</button>
            <button @click="changeTitle(newItem)">Cambiar título</button>
          </div>

          <ul>
            {{#each state.items as it}}
              <li>{{it}}</li>
            {{/each}}
          </ul>
        </fieldset>
        """;
    }
}
