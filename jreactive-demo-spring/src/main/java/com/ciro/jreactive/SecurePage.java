package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Authorize;
import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.annotations.Stateless;
import com.ciro.jreactive.router.Route;

/**
 * Demo de la anotación @Authorize.
 *
 * @Authorize a nivel clase: cualquier autenticado puede invocar @Call de esta página.
 * @Authorize(roles = {"ADMIN"}) a nivel método: solo ADMIN puede ejecutar revealSecret().
 */
@Route(path = "/secure")
@Stateless
@Authorize
public class SecurePage extends AppPage {

    @State public String message = "Acceso concedido. Estás autenticado.";
    @State public String secret = "";

    @Call
    public void ping() {
        this.message = "Pong: cualquier usuario autenticado puede hacer ping.";
        this.secret = "";
    }

    @Call
    @Authorize(roles = {"ADMIN"})
    public void revealSecret() {
        this.secret = "🥷 Clave maestra del boss: 42. No se lo digas a nadie.";
        this.message = "Secreto revelado: fuiste autorizado como ADMIN.";
    }

    @Override
    protected String template() {
        return """
            <div style="padding: 30px; font-family: system-ui, sans-serif; max-width: 640px; margin: 0 auto;">
                <h1>🔒 Demo @Authorize</h1>
                <p style="color: #555;">Ejemplo runnable de la anotación <code>@Authorize</code> conectada al hub de sesión.</p>

                <div style="margin-top: 20px; padding: 15px; background: #e7f3ff; border-radius: 8px; border-left: 5px solid #007bff;">
                    <strong>Estado:</strong> {{message}}
                </div>

                <div style="margin-top: 20px; display: flex; gap: 10px; flex-wrap: wrap;">
                    <button @click="ping()"
                        style="padding: 10px 15px; background: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer;">
                        📢 Ping (cualquier autenticado)
                    </button>
                    <button @click="revealSecret()"
                        style="padding: 10px 15px; background: #dc3545; color: white; border: none; border-radius: 4px; cursor: pointer;">
                        🔑 Revelar Secreto (solo ADMIN)
                    </button>
                </div>

                {{#if secret}}
                    <div style="margin-top: 20px; padding: 15px; background: #fff3cd; border-radius: 6px; border-left: 5px solid #ffc107;">
                        {{secret}}
                    </div>
                {{/if}}

                <div style="margin-top: 30px; padding: 15px; background: #f8f9fa; border-radius: 6px; font-size: 0.9em; color: #555;">
                    <strong>¿Cómo probarlo?</strong>
                    <ol style="margin: 10px 0 0 20px;">
                        <li>En la consola del navegador ejecuta:
<code>fetch('/demo/login', {method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({user:'admin', roles:['ADMIN']})})</code>
                        </li>
                        <li>Refresca esta página. Ahora eres ADMIN autenticado.</li>
                        <li>Pulsa "Revelar Secreto": debe funcionar.</li>
                        <li>Haz logout con <code>fetch('/demo/logout', {method:'POST'})</code>, refresca y vuelve a probar: devuelve <strong>FORBIDDEN</strong>.</li>
                    </ol>
                </div>

                <p style="margin-top: 30px;"><a data-router href="/">⬅️ Volver al Inicio</a></p>
            </div>
        """;
    }
}