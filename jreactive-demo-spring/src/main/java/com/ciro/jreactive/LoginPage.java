package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.annotations.Stateless;
import com.ciro.jreactive.router.Route;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

@Route(path = "/login")
@Stateless
public class LoginPage extends AppPage {

    // --- DTO con Bean Validation ---
    public static class LoginForm implements Serializable {
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "Formato de correo inválido")
        public String email;

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
        public String password;
    }

    // --- ESTADO REACTIVO ---
    @State 
    public LoginForm form = new LoginForm();

    @State 
    public String statusMessage = "";

    @State 
    public boolean isError = false;

    // --- ACCIONES DEL SERVIDOR ---
    @Call
    public void login(@Valid LoginForm form) {
        // Simulación de autenticación (Aquí iría tu lógica contra DB/Security)
        if ("ciro@jreactive.com".equals(form.email) && "123456".equals(form.password)) {
            this.statusMessage = "✅ ¡Bienvenido! Redirigiendo...";
            this.isError = false;
            
            // TODO: Inyectar cookie de sesión/JWT y ejecutar redirección real
        } else {
            this.statusMessage = "❌ Credenciales inválidas. Inténtalo de nuevo.";
            this.isError = true;
        }
        
        // Limpiamos el formulario por seguridad
        this.form = new LoginForm();
    }

    // --- VISTA (AST + Jsoup Engine) ---
    @Override
    protected String template() {
        return """
            <div style="display: flex; justify-content: center; align-items: center; padding: 40px 20px;">
                <section style="width: 100%; max-width: 400px;">
                    
                    <JCard title="Iniciar Sesión" subtitle="Accede a tu cuenta para continuar">
                        
                        <JForm onSubmit="login(form)">
                            <JInput
                                :field="form.email"
                                label="Correo Electrónico"
                                type="email"
                                placeholder="tu@correo.com"
                                required="true"
                                autocomplete="email"
                            />
                            
                            <JInput
                                :field="form.password"
                                label="Contraseña"
                                type="password"
                                placeholder="••••••••"
                                required="true"
                                autocomplete="current-password"
                            />
                        </JForm>

                        {{#if statusMessage}}
                            <div style="margin-top: 15px; padding: 12px; border-radius: 6px; font-weight: 500;
                                        color: {{#if isError}}#721c24{{else}}#155724{{/if}}; 
                                        background-color: {{#if isError}}#f8d7da{{else}}#d4edda{{/if}};">
                                {{statusMessage}}
                            </div>
                        {{/if}}
                        
                        <div style="margin-top: 20px; text-align: center; font-size: 0.9em;">
                            <p style="color: #666;">
                                ¿No tienes cuenta? <a data-router href="/signup2" style="color: #007bff; text-decoration: none; font-weight: bold;">Regístrate aquí</a>
                            </p>
                        </div>

                    </JCard>
                    
                </section>
            </div>
        """;
    }
}