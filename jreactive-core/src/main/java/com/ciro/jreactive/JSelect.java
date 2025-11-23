package com.ciro.jreactive;

import static com.ciro.jreactive.Type.$;

import java.util.ArrayList;
import java.util.List;

/**
 * Componente base de <select> para JReactive.
 *
 * Uso típico:
 *
 *   @Bind
 *   public Type<List<JSelect.Option>> countryOptions = $(new ArrayList<>());
 *
 *   @Bind
 *   public Type<String> country = $(""); // form.country
 *
 *   ...
 *
 *   <JSelect
 *     :field="form.country"
 *     :label="País"
 *     :options="countryOptions"
 *     placeholder="Selecciona un país"
 *     :required="true"
 *   />
 *
 * El atributo name del <select> será exactamente el valor de "field"
 * (ej: "form.country"), para encajar con Bean Validation y con
 * la construcción de objetos en buildValue().
 */
public class JSelect extends HtmlComponent {

    /**
     * Opción del select: value, label, disabled.
     */
    public static class Option {
        public String value;
        public String label;
        public boolean disabled;

        public Option() {
        }

        public Option(String value, String label) {
            this.value = value;
            this.label = label;
        }

        public Option(String value, String label, boolean disabled) {
            this.value = value;
            this.label = label;
            this.disabled = disabled;
        }
    }

    /** Nombre completo del campo: ej. "form.country". */
    @Bind
    public Type<String> field = $("");

    /** Texto visible de la etiqueta. */
    @Bind
    public Type<String> label = $("");

    /** Placeholder que se muestra como primera opción deshabilitada. */
    @Bind
    public Type<String> placeholder = $("");

    /** Marca si es requerido (se muestra * en la etiqueta). */
    @Bind
    public Type<Boolean> required = $(Boolean.FALSE);

    /** Marca si el select está deshabilitado (solo uso visual/CSS por ahora). */
    @Bind
    public Type<Boolean> disabled = $(Boolean.FALSE);

    /** Texto de ayuda debajo del select. */
    @Bind
    public Type<String> helpText = $("");

    /** Lista de opciones del select. */
    @Bind
    public Type<List<Option>> options = $(new ArrayList<>());

    public JSelect() {
        // defaults ya inicializados arriba
    }

    @Override
    protected String template() {
        return """
            <div class="jrx-field">
              <label>
                <span class="jrx-label">
                  {{label}}
                  {{#if required}}
                    <span class="jrx-required">*</span>
                  {{/if}}
                </span>

                <select
                  name="{{field}}"
                  class="jrx-select"
                >
                  {{#if placeholder}}
                    <option value="" disabled selected>{{placeholder}}</option>
                  {{/if}}

                  {{#each options as opt}}
                    <option value="{{opt.value}}">
                      {{opt.label}}
                    </option>
                  {{/each}}
                </select>
              </label>

              {{#if helpText}}
                <small class="jrx-help">{{helpText}}</small>
              {{/if}}
            </div>
            """;
    }
}
