package com.ciro.jreactive;

import static com.ciro.jreactive.Type.$;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JSelect extends HtmlComponent {

    public static class Option implements Serializable {
        public String value;
        public String label;
        public boolean disabled;

        public Option() {}
        
        public Option(String value, String label) {
            this.value = value;
            this.label = label;
            this.disabled = false;
        }
        
        public Option(String value, String label, boolean disabled) {
            this.value = value;
            this.label = label;
            this.disabled = disabled;
        }
    }

    // =========================================================
    // 🛠️ HELPERS: Métodos estáticos para crear opciones rápido
    // =========================================================
    
    /** Crea opciones a partir de una lista simple de strings */
    public static List<Option> from(String... values) {
        return Arrays.stream(values)
                .map(v -> new Option(v, v))
                .collect(Collectors.toList());
    }

    /** Crea opciones a partir de un Map (Clave = Value, Valor = Label) */
    public static List<Option> from(Map<String, String> map) {
        return map.entrySet().stream()
                .map(e -> new Option(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    /** Crea opciones a partir de un Enum */
    public static <T extends Enum<T>> List<Option> fromEnum(Class<T> enumClass) {
        return Arrays.stream(enumClass.getEnumConstants())
                .map(e -> new Option(e.name(), e.name()))
                .collect(Collectors.toList());
    }

    // =========================================================
    // 📦 ESTADO Y BINDINGS
    // =========================================================

    @Bind public Type<String> field = $("");        // Ej: form.country
    @Bind public Type<String> label = $("");        // Ej: País
    @Bind public Type<String> placeholder = $("");

    @Bind public Type<Boolean> required = $(false);
    @Bind public Type<Boolean> disabled = $(false);
    @Bind public Type<String> helpText = $("");

    @Bind public Type<List<Option>> options = $(new ArrayList<>());

    @Bind public String onChange = "";

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
                  @change="{{onChange}}"
                  :required="{{required}}"
                  :disabled="{{disabled}}"
                >
                  {{#if placeholder}}
                    <option value="" disabled selected hidden>{{placeholder}}</option>
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