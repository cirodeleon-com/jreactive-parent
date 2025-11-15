package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.router.Route;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Component;

@Component
@Route(path = "/signup")
public class SignupPage extends HtmlComponent {

    // 1) DTO con Bean Validation
    public static class SignupForm {

        @NotBlank(message = "El nombre es obligatorio")
        public String name;

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        public String email;

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
        public String password;
    }

    // 2) Estado de la página (para mostrar mensajes)
    public static class PageState {
        public String lastMessage;
    }

    @State
    PageState state = new PageState();

    // 3) Método @Call con @Valid: si falla, CallGuard devuelve JSON de VALIDATION
    @Call
    public void register(@Valid SignupForm form) {
        // Si la validación falla, NUNCA se entra aquí
        state.lastMessage = "Usuario " + form.name + " registrado correctamente";
        updateState("state"); // empuja el nuevo estado al front
    }

    // 4) Plantilla: inputs con name="form.xxx" y click "register(form)"
    @Override
    protected String template() {
        return """
            <section>
              <h1>Registro con Bean Validation</h1>

              <div>
                <label>
                  Nombre:
                  <input type="text"
                         name="form.name">
                </label>
              </div>

              <div>
                <label>
                  Correo:
                  <input type="email"
                         name="form.email">
                </label>
              </div>

              <div>
                <label>
                  Contraseña:
                  <input type="password"
                         name="form.password">
                </label>
              </div>

              <button type="button"
                      @click="register(form)">
                Registrarme
              </button>

              <p data-if="state.lastMessage">
                {{state.lastMessage}}
              </p>
            </section>
            """;
    }
}
