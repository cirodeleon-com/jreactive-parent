package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Authorize;
import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.router.Route;

/**
 * Demo interactivo de la anotación @Authorize.
 *
 * Flujo:
 *  1. El usuario llega como GUEST (adminMode = false).
 *  2. Los botones protegidos con @Authorize(roles={"ADMIN"}) son denegados por el DemoAuthorizationProvider.
 *  3. El usuario pulsa "Iniciar como ADMIN" (toggleAdmin), lo que pone adminMode = true.
 *  4. Ahora los métodos protegidos se ejecutan normalmente.
 */
@Route(path = "/security-demo")
public class SecurityDemoPage extends AppPage {

    @State public boolean adminMode = false;
    @State public String lastMessage = "";
    @State public boolean lastError = false;

    // --- Método público (sin @Authorize): siempre permitido ---

    @Call
    public void viewPublicData() {
        this.lastMessage = "📊 Datos públicos visibles para cualquier usuario.";
        this.lastError = false;
    }

    // --- Método de simulación de login ---

    @Call
    public void toggleAdmin() {
        this.adminMode = !this.adminMode;
        this.lastMessage = adminMode
                ? "✅ Sesión ADMIN iniciada. Los botones protegidos ya funcionan."
                : "🔒 Sesión cerrada. Los botones protegidos volverán a denegarse.";
        this.lastError = false;
    }

    // --- Métodos protegidos (@Authorize + roles) ---

    @Call
    @Authorize(roles = {"ADMIN"})
    public void exportData() {
        this.lastMessage = "📤 Exportación completada: backup-" + System.currentTimeMillis() + ".zip";
        this.lastError = false;
    }

    @Call
    @Authorize(roles = {"ADMIN"})
    public void deleteAllData() {
        this.lastMessage = "🗑️ ¡Todos los datos fueron borrados! (demo)";
        this.lastError = false;
    }

    @Override
    protected String template() {
        return """
            <div style="padding: 30px; font-family: system-ui, sans-serif; max-width: 600px; margin: auto;">
                <h1>🔐 Autorización con @Authorize</h1>
                <p>Protege métodos @Call por rol. El framework bloquea la llamada antes de que ejecute tu lógica.</p>

                <JCard title="Estado de Sesión" subtitle="Simulación de autenticación">
                    <p>Rol actual: <strong>{{#if adminMode}}ADMIN ✅{{else}}GUEST 👤{{/if}}</strong></p>
                    <button @click="toggleAdmin()"
                            style="padding: 8px 16px; cursor: pointer; margin-top: 10px; border: 1px solid #ccc; border-radius: 4px; background: white;">
                        {{#if adminMode}}🚪 Cerrar Sesión{{else}}🔑 Iniciar como ADMIN{{/if}}
                    </button>
                </JCard>

                <JCard title="Acciones" subtitle="La verde es pública; la azul y roja requieren ADMIN">

                    <div style="display: flex; flex-direction: column; gap: 10px; margin-top: 10px;">
                        <button @click="viewPublicData()"
                                style="padding: 10px; background: #28a745; color: white; border: none; border-radius: 4px; cursor: pointer;">
                            📖 Ver Datos Públicos (sin protección)
                        </button>

                        <button @click="exportData()"
                                style="padding: 10px; background: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer;">
                            📤 Exportar Datos (requiere ADMIN)
                        </button>

                        <button @click="deleteAllData()"
                                style="padding: 10px; background: #dc3545; color: white; border: none; border-radius: 4px; cursor: pointer;">
                            🗑️ Borrar Todo (requiere ADMIN)
                        </button>
                    </div>

                    {{#if lastMessage}}
                    <div style="margin-top: 15px; padding: 12px; border-radius: 6px;
                                background: {{#if lastError}}#f8d7da{{else}}#d4edda{{/if}};
                                color: {{#if lastError}}#721c24{{else}}#155724{{/if}};">
                        {{lastMessage}}
                    </div>
                    {{/if}}

                </JCard>

                <div style="margin-top: 20px; padding: 15px; background: #f8f9fa; border-radius: 8px; font-size: 0.9em; color: #555;">
                    <strong>¿Cómo funciona?</strong>
                    <ol style="margin: 8px 0 0 20px; padding: 0;">
                        <li>Pulsa "Exportar" sin sesión → verás un toast de error <code>FORBIDDEN</code>.</li>
                        <li>Pulsa "Iniciar como ADMIN" → el provider ve <code>adminMode = true</code>.</li>
                        <li>Pulsa "Exportar" de nuevo → se ejecuta correctamente.</li>
                    </ol>
                </div>

                <p style="margin-top: 20px;"><a data-router href="/">⬅️ Volver al Inicio</a></p>
            </div>
        """;
    }
}