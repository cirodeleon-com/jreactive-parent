package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Authorize;
import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.annotations.Stateful;
import com.ciro.jreactive.router.Route;
import com.ciro.jreactive.smart.SmartList;
import java.util.List;

@Route(path = "/secure-demo")
@Stateful
public class SecureDemoPage extends AppPage {

    @State public SmartList<String> items;
    @State public String message = "Bienvenido a la demo de seguridad";
    @State public String currentRole = "GUEST";
    @State public boolean isAdmin = false;

    @Override
    public void onInit() {
        if (items == null) {
            items = new SmartList<>(List.of("Item Semilla 1", "Item Semilla 2", "Item Semilla 3"));
        }
    }

    @Call
    public void addItem(String newItem) {
        if (newItem != null && !newItem.isBlank()) {
            items.add(newItem);
            message = "✅ Item agregado: " + newItem;
        }
    }

    @Authorize(roles = {"ADMIN"})
    @Call
    public void deleteItem(String item) {
        items.remove(item);
        message = "🗑️ Item eliminado: " + item;
    }

    @Call
    public void login(String role) {
        this.currentRole = role;
        this.isAdmin = "ADMIN".equals(role);
        message = "🔐 Rol cambiado a: " + role;
    }

    @Call
    public void logout() {
        this.currentRole = "GUEST";
        this.isAdmin = false;
        message = "👋 Sesión cerrada. Rol actual: GUEST";
    }

    @Override
    protected String template() {
        return """
            <div style="padding: 30px; font-family: system-ui, sans-serif; max-width: 700px; margin: 0 auto;">
                <h1>🔒 Demo de Seguridad (@Authorize)</h1>

                <div style="background: #e3f2fd; padding: 15px; border-radius: 8px; margin-bottom: 20px; display: flex; align-items: center; gap: 10px;">
                    <strong>Rol actual:</strong>
                    <span style="background: #6c757d; color: white; padding: 4px 12px; border-radius: 12px; font-weight: bold;">
                        {{currentRole}}
                    </span>
                </div>

                <div style="background: #f8f9fa; padding: 15px; border-radius: 8px; margin-bottom: 20px;">
                    <strong>Cambiar rol (simulado):</strong>
                    <div style="display: flex; gap: 10px; margin-top: 10px; flex-wrap: wrap;">
                        <button @click="login('ADMIN')" style="padding: 8px 16px; background: #28a745; color: white; border: none; border-radius: 4px; cursor: pointer;">
                            🔑 ADMIN
                        </button>
                        <button @click="login('USER')" style="padding: 8px 16px; background: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer;">
                            👤 USER
                        </button>
                        <button @click="login('GUEST')" style="padding: 8px 16px; background: #ffc107; color: #333; border: none; border-radius: 4px; cursor: pointer;">
                            👋 GUEST
                        </button>
                        <button @click="logout()" style="padding: 8px 16px; background: #dc3545; color: white; border: none; border-radius: 4px; cursor: pointer;">
                            🚪 Logout
                        </button>
                    </div>
                </div>

                {{#if message}}
                    <div style="padding: 10px; margin-bottom: 20px; background: #fff3cd; border: 1px solid #ffeaa7; border-radius: 6px; font-weight: 500;">
                        {{message}}
                    </div>
                {{/if}}

                <div style="margin-bottom: 20px;">
                    <h3>Agregar Item</h3>
                    <div style="display: flex; gap: 10px;">
                        <input type="text" name="newItem" placeholder="Nuevo item..." style="flex: 1; padding: 8px; border: 1px solid #ccc; border-radius: 4px;" />
                        <button @click="addItem(newItem)" style="padding: 8px 16px; background: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer;">
                            ➕ Agregar
                        </button>
                    </div>
                </div>

                <h3>Lista de Items ({{items.size}})</h3>
                <ul style="list-style: none; padding: 0;">
                    {{#each items as item}}
                        <li style="padding: 10px; background: white; border: 1px solid #eee; border-radius: 4px; margin-bottom: 8px; display: flex; justify-content: space-between; align-items: center;">
                            <span>{{item}}</span>
                            {{#if isAdmin}}
                                <button @click="deleteItem(item)" style="padding: 4px 10px; background: #dc3545; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 0.85em;">
                                    🗑️ Eliminar
                                </button>
                            {{/if}}
                        </li>
                    {{/each}}
                </ul>

                <div style="margin-top: 30px; padding: 15px; background: #fff3cd; border-radius: 8px; font-size: 0.9em; color: #856404;">
                    <strong>💡 Cómo probar @Authorize:</strong>
                    <ol style="margin: 8px 0 0 20px;">
                        <li>Sin login (GUEST): El botón de eliminar NO aparece.</li>
                        <li>Login ADMIN: El botón aparece. deleteItem tiene @Authorize(roles={"ADMIN"}).</li>
                        <li>Login USER/GUEST: El botón desaparece (isAdmin = false).</li>
                        <li>El servidor valida autorización en cada @Call con @Authorize (fail-closed).</li>
                    </ol>
                </div>

                <p style="margin-top: 20px;"><a data-router href="/">⬅️ Volver al Inicio</a></p>
            </div>
        """;
    }
}