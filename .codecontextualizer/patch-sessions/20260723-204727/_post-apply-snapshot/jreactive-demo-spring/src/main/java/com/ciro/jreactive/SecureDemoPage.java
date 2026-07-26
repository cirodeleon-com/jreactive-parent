package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.annotations.Stateful;
import com.ciro.jreactive.router.Route;
import com.ciro.jreactive.smart.SmartList;

@Route(path = "/secure-demo")
@Stateful
public class SecureDemoPage extends AppPage {

    @State public SmartList<String> items = new SmartList<>();
    @State public String message = "Bienvenido. Tu rol actual es GUEST.";
    @State public String currentRole = "GUEST";
    @State public String newItemText = "";

    @Override
    public void onInit() {
        items.add("Item Semilla A");
        items.add("Item Semilla B");
        items.add("Item Semilla C");
    }

    @Call
    public void addItem(String value) {
        if (value == null || value.isBlank()) return;
        items.add(value);
        newItemText = "";
        message = "Item añadido correctamente.";
    }

    @Override
    protected String template() {
        return """
            <div style="padding: 30px; font-family: system-ui, sans-serif; max-width: 600px; margin: 0 auto;">
                <h1>🔒 Demo Segura</h1>
                <p>Rol actual: <strong>{{currentRole}}</strong></p>
                <p>{{message}}</p>

                <div style="margin-bottom: 20px; display: flex; gap: 10px;">
                    <input type="text" name="newItemText" placeholder="Escribe un nuevo item..." style="flex: 1; padding: 8px;" />
                    <button @click="addItem(newItemText)" style="padding: 8px 16px; background: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer;">Agregar</button>
                </div>

                <h3>Lista de Items ({{items.size}})</h3>
                <ul style="border: 1px solid #ddd; padding: 10px 20px; min-height: 60px; border-radius: 4px;">
                    {{#each items as item}}
                        <li style="padding: 4px 0;">{{item}}</li>
                    {{/each}}
                </ul>

                <p style="margin-top: 30px;"><a data-router href="/">⬅️ Volver al Inicio</a></p>
            </div>
        """;
    }
}