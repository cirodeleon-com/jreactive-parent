
package com.ciro.jreactive;

import static com.ciro.jreactive.Type.$;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.router.Route;

@Component
@Route(path = "/signup-country")
public class SignupCountryPage extends HtmlComponent {

    public static class SignupForm {
        public String country;
    }

    // Lo que viene del formulario
    @State
    SignupForm form = new SignupForm();

    // Opciones para el select
    @Bind
    public Type<List<JSelect.Option>> countries = $(List.of(
        new JSelect.Option("co", "Colombia"),
        new JSelect.Option("ve", "Venezuela"),
        new JSelect.Option("cl", "Chile")
    ));

    // 🔥 State derivado: el nombre bonito del país
    @State
    String selectedCountryLabel = "";

    @Call
    public void register(SignupForm form) {
        System.out.println("País elegido: " + form.country);
        // aquí lo que tú quieras
    }

    // 🔥 Este método solo actualiza el label y hace updateState
    @Call
    public void refreshCountryLabel(SignupForm form) {
        String label = countries.get().stream()
                .filter(o -> Objects.equals(o.value, form.country))
                .map(o -> o.label)
                .findFirst()
                .orElse("");

        selectedCountryLabel = label;
        // Empuja el nuevo valor al frontend
        updateState("selectedCountryLabel");
    }

    @Override
    protected String template() {
        return """
          <section style="max-width: 420px; padding: 16px; font-family: system-ui;">
            <h1>Registro con país</h1>

            <form>
              <JSelect
                :field="form.country"
                label="País"
                :options="countries"
                placeholder="Selecciona un país"
                required="true"
              />

              {{#if selectedCountryLabel}}
                <h2>País seleccionado: {{selectedCountryLabel}}</h2>
              {{/if}}

              <JButton
                label="Ver país seleccionado"
                onClick="refreshCountryLabel(form)"
              />

              <JButton
                label="Registrarme"
                onClick="register(form)"
              />
            </form>
          </section>
          """;
    }

}
