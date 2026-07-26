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
              <div class="secure-demo">
                  <style>
                      .secure-demo {
                          max-width: 800px;
                          margin: 0 auto;
                          padding: 40px 20px;
                          font-family: system-ui, -apple-system, sans-serif;
                          color: #1e293b;
                      }
                      .secure-demo h2 {
                          color: #0f172a;
                          margin: 0 0 16px 0;
                      }
                      .role-tag {
                          display: inline-block;
                          padding: 4px 12px;
                          background: #dcfce7;
                          color: #166534;
                          border-radius: 999px;
                          font-size: 0.85rem;
                          font-weight: 600;
                      }
                      .state-message {
                          padding: 10px 14px;
                          background: #f1f5f9;
                          border: 1px solid #e2e8f0;
                          border-radius: 6px;
                          margin: 12px 0;
                      }
                      table {
                          width: 100%;
                          border-collapse: collapse;
                          margin-top: 16px;
                      }
                      th, td {
                          padding: 10px 12px;
                          border: 1px solid #e2e8f0;
                          text-align: left;
                      }
                      th {
                          background: #f8fafc;
                          font-weight: 600;
                      }
                      form {
                          display: flex;
                          gap: 10px;
                          margin-top: 20px;
                      }
                      input[type=text] {
                          flex: 1;
                          padding: 8px 12px;
                          border: 1px solid #cbd5e1;
                          border-radius: 6px;
                          font-size: 1rem;
                      }
                      button {
                          padding: 8px 18px;
                          background: #2563eb;
                          color: #fff;
                          border: none;
                          border-radius: 6px;
                          font-weight: 600;
                          cursor: pointer;
                      }
                      button:hover {
                          background: #1d4ed8;
                      }
                  </style>

                  <h2>🔐 Secure Demo Page</h2>
                  <p>Rol actual: <span class="role-tag">${currentRole}</span></p>
                  <p class="state-message">${message}</p>

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