package com.ciro.jreactive;

import static com.ciro.jreactive.Type.$;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.router.Route;

import jakarta.validation.constraints.NotBlank;

@Component
@Route(path = "/signup-country")
public class SignupCountryPage extends HtmlComponent {

    public static class SignupForm {
        public String country;
    }

    @State
    SignupForm form = new SignupForm();

    @Bind
    public Type<java.util.List<JSelect.Option>> countries = $(
        java.util.List.of(
            new JSelect.Option("co", "Colombia"),
            new JSelect.Option("ve", "Venezuela"),
            new JSelect.Option("cl", "Chile")
        )
    );

    @Call
    public void register(SignupForm form) {
        System.out.println("País elegido: " + form.country);
    }

    @Override
    protected String template() {
        return """
          <section style="max-width: 420px; padding: 16px; font-family: system-ui;">
            <h1>Registro con país</h1>

            <form>
              <JSelect
                :field="form.country"
                :label="País"
                :options="countries"
                :placeholder="Selecciona un país"
                :required="true"
              />

              <JButton :label="Registrarme" @click="register(form)" />
            </form>
          </section>
          """;
    }
}

