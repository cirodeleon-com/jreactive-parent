package com.ciro.jreactive;

import static com.ciro.jreactive.Type.$;

public class JInput extends HtmlComponent {

    @Bind public Type<String> field = $("");       // form.email
    @Bind public Type<String> label = $("");       // Email
    @Bind public Type<String> type = $("text");
    @Bind public Type<String> placeholder = $("");
    @Bind public Type<Boolean> required = $(false);
    @Bind public Type<Boolean> disabled = $(false);
    @Bind public Type<String> helpText = $("");
    @Bind public Type<String> autocomplete = $("");

    // eventos (pueden venir vacíos)
    @Bind public String onInput = "";
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

                <input
                  type="{{type}}"
                  name="{{field}}"
                  placeholder="{{placeholder}}"
                  autocomplete="{{autocomplete}}"
                  @input="{{onInput}}"
                  @change="{{onChange}}"
                />
              </label>

              {{#if helpText}}
                <small class="jrx-help">{{helpText}}</small>
              {{/if}}
            </div>
        """;
    }
}
