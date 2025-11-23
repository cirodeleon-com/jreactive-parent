package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.router.Route;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

import org.springframework.stereotype.Component;

@Component
@Route(path = "/signup2")
public class SignupPage2 extends HtmlComponent {

    // 1) DTO con Bean Validation
    public static class SignupForm implements Serializable {

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

    @Override
    protected String template() {
        return """
            <section style="max-width: 420px; padding: 16px; font-family: system-ui;">
              <h1>Registro con Bean Validation</h1>

              <form class="jrx-form">
                <JInput
                  :field="form.name"
                  :label="Nombre"
                  :type="text"
                  :placeholder="Tu nombre"
                  :required="true"
                  :autocomplete="name"
                />

                <JInput
                  :field="form.email"
                  :label="Correo"
                  :type="email"
                  :placeholder="correo@ejemplo.com"
                  :required="true"
                  :autocomplete="email"
                />

                <JInput
                  :field="form.password"
                  :label="Contraseña"
                  :type="password"
                  :placeholder="Mínimo 8 caracteres"
                  :required="true"
                  :autocomplete="new-password"
                />

                <JButton label="Registrarme" @click="register(form)" />
              </form>

              <p>{{state.lastMessage}}</p>
            </section>
            """;
    }

    



}

