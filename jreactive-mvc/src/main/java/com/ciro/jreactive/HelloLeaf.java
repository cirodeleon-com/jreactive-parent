package com.ciro.jreactive;

import static com.ciro.jreactive.Type.$;
import java.util.ArrayList;
import java.util.List;
import com.ciro.jreactive.annotations.Call;

public class HelloLeaf extends HtmlComponent {

    /* 1️⃣  Haz la lista MUTABLE + reactiva */
    @Bind
    public Type<List<String>> fruits =
        $(new ArrayList<>(List.of("Apple", "Banana", "Cherry")));

    @Bind
    public Type<Boolean> showHello = $(Boolean.TRUE);

    @Bind
    public Type<String> newFruit  = $("");

    /* 2️⃣  Añade elemento y dispara notificación */
    @Call
    public void addFruit(String fruta) {
        if (fruta == null || fruta.isBlank()) return;

        // 1️⃣ copia la lista actual
        var copy = new ArrayList<>(fruits.get());
        copy.add(fruta);

        // 2️⃣ envía la NUEVA instancia
        fruits.set(copy);

        newFruit.set("");
        System.out.println("GUARDADA: " + fruta+" size" + fruits.get().size());
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
