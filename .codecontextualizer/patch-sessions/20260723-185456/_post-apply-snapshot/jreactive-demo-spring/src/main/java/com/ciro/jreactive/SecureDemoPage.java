package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Authorize;
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

    @Call
    public void login(String role) {
        if (role != null && (role.equals("ADMIN") || role.equals("USER") || role.equals("GUEST"))) {
            currentRole = role;
            message = "🔑 Rol cambiado a: " + role;
        } else {
            message = "⚠️ Rol inválido: " + role;
        }
    }

    @Authorize(roles = {"ADMIN"})
    @Call
    public void deleteItem(String item) {
        if (items.remove(item)) {
            message = "🗑️ Item eliminado: \"" + item + "\" (total: " + items.size() + ")";
        } else {
            message = "⚠️ No se encontró el item para eliminar.";
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
                          display: flex;
                          align-items: center;
                          justify-content: space-between;
                      }
                      .btn-delete {
                          background: #ef4444;
                          color: white;
                          border: none;
                          border-radius: 6px;
                          padding: 4px 12px;
                          cursor: pointer;
                          font-size: 0.85rem;
                          font-weight: 600;
                          transition: background 0.2s;
                      }
                      .btn-delete:hover {
                          background: #dc2626;
                      }
                      .role-controls {
                          display: flex;
                          gap: 10px;
                          margin-bottom: 24px;
                          flex-wrap: wrap;
                      }
                      .btn-role {
                          padding: 8px 16px;
                          border: none;
                          border-radius: 8px;
                          font-size: 0.9rem;
                          font-weight: 600;
                          cursor: pointer;
                          transition: background 0.2s;
                      }
                      .btn-role.user {
                          background: #10b981;
                          color: white;
                      }
                      .btn-role.user:hover {
                          background: #059669;
                      }
                      .btn-role.admin {
                          background: #7c3aed;
                          color: white;
                      }
                      .btn-role.admin:hover {
                          background: #6d28d9;
                      }
                      .btn-role.logout {
                          background: #64748b;
                          color: white;
                      }
                      .btn-role.logout:hover {
                          background: #475569;
                      }
                  </style>

                  <h1>🔐 Secure Demo</h1>
                  <span class="role-badge">Rol actual: ${currentRole}</span>

                  <div class="info-message">${message}</div>

                  <div class="role-controls">
                      {{#if currentRole == 'GUEST'}}
                          <button class="btn-role user" @click="login('USER')">🔑 Login como USER</button>
                          <button class="btn-role admin" @click="login('ADMIN')">👑 Login como ADMIN</button>
                      {{/if}}
                      {{#if currentRole == 'USER'}}
                          <button class="btn-role admin" @click="login('ADMIN')">👑 Subir a ADMIN</button>
                          <button class="btn-role logout" @click="login('GUEST')">🚪 Logout</button>
                      {{/if}}
                      {{#if currentRole == 'ADMIN'}}
                          <button class="btn-role logout" @click="login('GUEST')">🚪 Logout</button>
                      {{/if}}
                  </div>

                  <div class="add-form">
                      <input type="text" bind="newItem" placeholder="Escribe un nuevo item..." />
                      <button @click="addItem(newItem)">Agregar</button>
                  </div>

                  <ul class="item-list">
                      <li j-for="item : items">
                          <span>${item}</span>
                          {{#if currentRole == 'ADMIN'}}
                              <button class="btn-delete" @click="deleteItem(item)">🗑️ Eliminar</button>
                          {{/if}}
                      </li>
                  </ul>
              </div>
              """;
    }
}