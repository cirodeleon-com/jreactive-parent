package com.ciro.jreactive;

import static com.ciro.jreactive.Type.$;

public class JCheckBox extends HtmlComponent {

    @Bind public Type<String> field = $("");      // form.acceptTerms
    @Bind public Type<String> label = $("");      
    @Bind public Type<Boolean> required = $(false);
    @Bind public Type<Boolean> disabled = $(false);
    @Bind public Type<Boolean> checked = $(false);
    @Bind public Type<String> helpText = $("");

    // evento (puede venir vacío)
    @Bind public String onChange = "";

    @Override
    protected String template() {
        return """
            <div class="jrx-field jrx-checkbox">
              <label>
                <input
                  type="checkbox"
                  name="{{field}}"
                  @change="{{onChange}}"
                />

                <span class="jrx-label">
                  {{label}}
                  {{#if required}}
                    <span class="jrx-required">*</span>
                  {{/if}}
                </span>
              </label>

              {{#if helpText}}
                <small class="jrx-help">{{helpText}}</small>
              {{/if}}
            </div>
        """;
    }

}
