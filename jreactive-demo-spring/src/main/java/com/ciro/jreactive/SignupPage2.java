/* === File: jreactive-demo-spring\src\main\java\com\ciro\jreactive\SignupPage2.java === */
package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.router.Route;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Component
@Route(path = "/signup2")
public class SignupPage2 extends HtmlComponent {

    public static class SignupForm implements Serializable {
        @NotBlank public String name;
        @NotBlank @Email public String email;
        @NotBlank @Size(min = 8) public String password;
        public boolean acceptTerms;
    }

    public static class PageState {
        public String lastMessage;
    }

    @State
    SignupForm form = new SignupForm();

    @State
    PageState state = new PageState();

    // Estado reactivo para el checkbox (si lo quieres controlar desde el back)
    @State
    Boolean acceptTerms = false;

    @Call
    public void register(@Valid SignupForm form) {
        if (!form.acceptTerms) {
            state.lastMessage = "Debes aceptar los términos";
        } else {
            state.lastMessage = "Usuario " + form.name + " registrado correctamente";
        }
        updateState("state");
    }

    @Override
    protected String template() {
        return """
            <section style="max-width: 420px; padding: 16px; font-family: system-ui;">
              <h1>Registro con Bean Validation</h1>

              <JForm @click="register(form)">
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

                <JCheckBox
                  field="form.acceptTerms"
                  :checked="acceptTerms"
                  :required="true"
                  label="Acepto los términos y condiciones"
                />
              </JForm>

              <p>{{state.lastMessage}}</p>
            </section>
            """;
    }
}

