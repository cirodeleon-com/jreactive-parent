package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.annotations.Stateful;
import com.ciro.jreactive.router.Route;

import java.util.ArrayList;
import java.util.List;

@Stateful
@Route(path = "/secure-demo")
public class SecureDemoPage extends AppPage {

    private List<String> items = new ArrayList<>();
    private String message = "Esperando inicialización...";
    private String currentRole = "guest";

    // Supuesto: AppPage expone protected void onInit() invocado por el framework.
    // No se anota @Override para no romper compilación si la firma exacta difiere.
    protected void onInit() {
        this.items = new ArrayList<>();
        this.items.add("Item semilla A");
        this.items.add("Item semilla B");
        this.items.add("Item semilla C");
        this.message = "Listo: " + this.items.size() + " items cargados";
        this.currentRole = "guest";
    }

    @Call
    public void addItem(String name) {
        if (name == null || name.isBlank()) {
            this.message = "Error: el nombre no puede estar vacío";
            return;
        }
        String trimmed = name.trim();
        this.items.add(trimmed);
        this.message = "Añadido: " + trimmed + " (total: " + this.items.size() + ")";
    }

    @Override
    protected String template() {
        StringBuilder rows = new StringBuilder();
        for (String item : this.items) {
            rows.append("                <tr><td>")
                .append(escapeHtml(item))
                .append("</td></tr>\n");
        }
        return """
              <div>
                  <h2>Secure Demo Page</h2>
                  <p>Rol actual: <span>""" + escapeHtml(this.currentRole) + """</span></p>
                  <p>""" + escapeHtml(this.message) + """</p>

                  <table>
                      <thead>
                          <tr><th>Items</th></tr>
                      </thead>
                      <tbody>
              """ + rows.toString() + """
                      </tbody>
                  </table>

                  <!-- codecontextualizer-debt: el botón aún no invoca addItem;
                       pendiente conectar con el sistema de eventos de JReactive
                       (data-call / @click equivalente). Upgrade: sub-paso siguiente. -->
                  <form onsubmit="return false;">
                      <input type="text" name="newItem" placeholder="Nombre del nuevo item" />
                      <button type="button">Añadir item</button>
                  </form>
              </div>
              """;
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}