package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Authorize;
import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.annotations.Stateful;
import com.ciro.jreactive.router.Route;

import java.util.ArrayList;
import java.util.List;

@Route(path = "/secure-demo")
@Stateful
public class SecureDemoPage extends AppPage {

    @State public List<String> items = new ArrayList<>();
    @State public String message = "Bienvenido a la demo de seguridad.";
    @State public String currentRole = "GUEST";
    @State public String newItem = "";

    @Override
    public void onInit() {
        items.add("Item semilla A");
        items.add("Item semilla B");
        items.add("Item semilla C");
    }

    @Call
    public void addItem(String value) {
        if (value == null || value.isBlank()) return;
        items.add(value);
        message = "Item agregado: " + value;
        newItem = "";
    }

    @Call
    public void login(String role) {
        if (role == null || role.isBlank()) {
            currentRole = "GUEST";
            message = "Sesión cerrada. Rol: GUEST.";
            return;
        }
        String normalized = role.trim().toUpperCase();
        if (normalized.equals("ADMIN") || normalized.equals("USER") || normalized.equals("GUEST")) {
            currentRole = normalized;
            message = "Rol cambiado a " + normalized + ".";
        } else {
            currentRole = "GUEST";
            message = "Rol desconocido '" + role + "'. Resetado a GUEST.";
        }
    }

    @Authorize(roles = {"ADMIN"})
    @Call
    public void deleteItem(String item) {
        if (item == null) return;
        items.remove(item);
        message = "Item eliminado: " + item;
    }

    @Override
    protected String template() {
        return """
            <div style="padding: 30px; font-family: system-ui, sans-serif; max-width: 600px; margin: 0 auto;">
                <h1>🔒 Demo de Seguridad (Roles)</h1>
                <p style="color: #666;">Rol actual: <strong>{{currentRole}}</strong></p>
                <p>{{message}}</p>

                <div style="background: #f8f9fa; padding: 20px; border-radius: 8px; margin-bottom: 20px;">
                    <h3>Lista de Items ({{items.size}})</h3>

                    <div style="display: flex; gap: 10px; margin-bottom: 15px;">
                        <input type="text"
                               name="newItem"
                               placeholder="Escribe un nuevo item..."
                               style="flex: 1; padding: 8px; border: 1px solid #ccc; border-radius: 4px;" />
                        <button @click="addItem(newItem)"
                                style="padding: 8px 16px; background: #28a745; color: white; border: none; border-radius: 4px; cursor: pointer;">
                            ➕ Agregar
                        </button>
                    </div>

                    <ul style="list-style: none; padding: 0;">
                        {{#each items as item}}
                            <li style="padding: 8px 12px; background: white; border: 1px solid #eee; border-radius: 4px; margin-bottom: 6px; display: flex; justify-content: space-between; align-items: center;">
                                {{item}}
                                <button @click="deleteItem(item)"
                                        style="padding: 4px 10px; background: #dc3545; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 0.85em;">
                                    🗑️ Eliminar
                                </button>
                            </li>
                        {{/each}}
                    </ul>
                </div>

                <p><a data-router href="/">⬅️ Volver al Inicio</a></p>
            </div>
        """;
    }
}