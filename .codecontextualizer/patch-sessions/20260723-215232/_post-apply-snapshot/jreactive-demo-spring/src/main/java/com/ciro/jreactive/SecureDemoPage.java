package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Stateful;
import com.ciro.jreactive.router.Route;

import java.util.ArrayList;
import java.util.List;

@Route(path = "/secure-demo")
@Stateful
public class SecureDemoPage extends AppPage {

    @State
    public List<String> items = new ArrayList<>();

    @State
    public String message = "Bienvenido a la Demo Segura. El control de roles está activo.";

    @State
    public String currentRole = "GUEST";

    @Override
    public void onInit() {
        items.add("Item público #1");
        items.add("Item público #2");
        items.add("Item público #3");
    }

    @Override
    protected String template() {
        return """
            <div style="padding: 40px; font-family: system-ui, sans-serif; max-width: 600px; margin: 0 auto;">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                    <h1>🔒 Demo Segura</h1>
                    <span style="background: #e9ecef; padding: 6px 14px; border-radius: 20px; font-weight: 600; font-size: 0.9em;">
                        Rol: {{currentRole}}
                    </span>
                </div>

                <div style="background: #f8f9fa; border-left: 4px solid #007bff; padding: 12px 16px; margin-bottom: 20px; border-radius: 4px;">
                    {{message}}
                </div>

                <h3>Items disponibles</h3>
                <ul style="border: 1px solid #e2e8f0; border-radius: 8px; padding: 16px; background: white;">
                    {{#each items as item}}
                        <li style="padding: 8px 0; border-bottom: 1px solid #f1f5f9;">{{item}}</li>
                    {{/each}}
                </ul>

                <p style="margin-top: 30px;"><a data-router href="/">⬅️ Volver al Inicio</a></p>
            </div>
        """;
    }
}