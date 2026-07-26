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
    private String message = "";
    private String currentRole = "GUEST";
    private String newItem = "";

    @Override
    public void onInit() {
        items = new ArrayList<>(List.of("Item semilla 1", "Item semilla 2", "Item semilla 3"));
        message = "Bienvenido al Secure Demo. Agrega items a la lista.";
        currentRole = "GUEST";
        newItem = "";
    }

    @Call
    public void addItem(String item) {
        if (item != null && !item.isBlank()) {
            items.add(item.trim());
            message = "Item agregado: \"" + item.trim() + "\" (total: " + items.size() + ")";
            newItem = "";
        } else {
            message = "⚠️ El item no puede estar vacío.";
        }
    }

    @Override
    protected String template() {
        return """
              <div class="secure-demo">
                  <style>
                      .secure-demo {
                          max-width: 700px;
                          margin: 0 auto;
                          padding: 40px 20px;
                          font-family: system-ui, -apple-system, sans-serif;
                          color: #1e293b;
                      }
                      .secure-demo h1 {
                          font-size: 2rem;
                          font-weight: 700;
                          color: #0f172a;
                          margin: 0 0 8px 0;
                      }
                      .role-badge {
                          display: inline-block;
                          padding: 4px 12px;
                          background: #e0e7ff;
                          color: #3730a3;
                          border-radius: 20px;
                          font-size: 0.85rem;
                          font-weight: 600;
                          margin-bottom: 20px;
                      }
                      .info-message {
                          padding: 12px 16px;
                          background: #f0fdf4;
                          border: 1px solid #bbf7d0;
                          border-radius: 8px;
                          color: #166534;
                          margin-bottom: 24px;
                      }
                      .add-form {
                          display: flex;
                          gap: 10px;
                          margin-bottom: 24px;
                      }
                      .add-form input {
                          flex: 1;
                          padding: 10px 14px;
                          border: 1px solid #cbd5e1;
                          border-radius: 8px;
                          font-size: 1rem;
                          outline: none;
                      }
                      .add-form input:focus {
                          border-color: #3b82f6;
                          box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
                      }
                      .add-form button {
                          padding: 10px 20px;
                          background: #2563eb;
                          color: white;
                          border: none;
                          border-radius: 8px;
                          font-size: 1rem;
                          font-weight: 600;
                          cursor: pointer;
                          transition: background 0.2s;
                      }
                      .add-form button:hover {
                          background: #1d4ed8;
                      }
                      .item-list {
                          list-style: none;
                          padding: 0;
                          margin: 0;
                      }
                      .item-list li {
                          padding: 12px 16px;
                          background: #f8fafc;
                          border: 1px solid #e2e8f0;
                          border-radius: 8px;
                          margin-bottom: 8px;
                      }
                  </style>

                  <h1>🔐 Secure Demo</h1>
                  <span class="role-badge">Rol actual: ${currentRole}</span>

                  <div class="info-message">${message}</div>

                  <div class="add-form">
                      <input type="text" bind="newItem" placeholder="Escribe un nuevo item..." />
                      <button @click="addItem(newItem)">Agregar</button>
                  </div>

                  <ul class="item-list">
                      <li j-for="item : items">${item}</li>
                  </ul>
              </div>
              """;
    }
}