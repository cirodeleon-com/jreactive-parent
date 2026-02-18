package com.ciro.jreactive;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import static com.ciro.jreactive.Type.$;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.router.Route;

@Component
@Route(path = "/newStateTest")
public class NewStateTestPage extends AppPage {

    

    // 2) Input normal con @Bind + Type (esto sí sigue con Type)
    @State
    public String newItem = "";

    @State
    public String title = "Demo @State sin Type";
    
    @State
    public List<String> items = new ArrayList<>();
    
    
    @Override
    public void onInit(){
    	items.add("pera");
    	items.add("coca cola");
    	items.add("cero");
    	newItem="o";
    }

    @Call
    public void addItem(String value) {
        items.add(value);
        this.newItem="";
    }

    @Call
    public void resetList() {
        items.clear();
    }

    @Call
    public void changeTitle(String title) {
        this.title = title;
    }

    @Override
    protected String template() {
        return """
        <fieldset style='border:1px solid #888;padding:8px;width:360px'>
          <legend>{{title}}</legend>

          <div>
            <strong>Total:</strong>
            {{items.size}}
          </div>

          <div style='margin:8px 0'>
            <input name="newItem" placeholder="Nuevo item">
            <button @click="addItem(newItem)">Agregar</button>
            <button @click="resetList()">Limpiar</button>
            <button @click="changeTitle(newItem)">Cambiar título</button>
          </div>

          <ul>
            {{#each items as it}}
              <li>{{it}}</li>
            {{/each}}
          </ul>
        </fieldset>
        """;
    }
}
