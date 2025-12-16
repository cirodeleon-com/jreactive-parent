package com.ciro.jreactive;

import static com.ciro.jreactive.Type.$;
import java.util.ArrayList;
import java.util.List;
import com.ciro.jreactive.annotations.Call;

import jakarta.validation.constraints.NotBlank;

public class HelloLeaf extends HtmlComponent {

    /* 1️⃣  Haz la lista MUTABLE + reactiva */
    @State
    public List<String> fruits = new ArrayList<String>();

    @Bind
    public Type<Boolean> showHello = $(Boolean.TRUE);

    @State
    public String newFruit  = "";

    
    @Call
    public void addFruit(@NotBlank String fruta) {
        if (fruta == null || fruta.isBlank()) return;
       
        fruits.add(fruta);
        newFruit="";
        
        System.out.println("GUARDADA: " + fruta+" size" + fruits.size());
    }



    /* 3️⃣  Plantilla sin cambios */
    @Override
    protected String template() {
        return """
          <div class="page">
            {{#if showHello}}
              <ul>
                {{#each fruits as fruit}}
                  <li>{{fruit}}</li>
                {{/each}}
              </ul>
            {{/if}}

            <label>Mostrar lista</label>
            <input type="checkbox" name="showHello"/>
            <input name="newFruit" placeholder="Nueva fruta">
            <button @click="addFruit(newFruit)">Añadir</button>
          </div>
          """;
    }
}
