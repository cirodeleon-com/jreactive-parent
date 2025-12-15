package com.ciro.jreactive;

import static com.ciro.jreactive.Type.$;
import java.util.ArrayList;
import java.util.List;

public class JSelect extends HtmlComponent {

    public static class Option {
        public String value;
        public String label;
        public boolean disabled;

        public Option() {}
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

    @Bind public Type<String> field = $("");        // form.country
    @Bind public Type<String> label = $("");        // País
    @Bind public Type<String> placeholder = $("");

    @Bind public Type<Boolean> required = $(false);
    @Bind public Type<Boolean> disabled = $(false);
    @Bind public Type<String> helpText = $("");

    @Bind public Type<List<Option>> options = $(new ArrayList<>());

    // evento (puede venir vacío)
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
