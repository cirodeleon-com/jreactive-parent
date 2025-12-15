package com.ciro.jreactive;

public class JButton extends HtmlComponent {

    @Bind public String label = "Botón";
    @Bind public String type = "primary";
    @Bind public boolean loading = false;
    @Bind public boolean disabled = false;

    // 🔥 evento real
    @Bind public String onClick = "";

    @Override
    protected String template() {
        return """
            <button
              class="jr-btn {{type}}"
              @click="{{onClick}}"
              :disabled="{{disabled}} || {{loading}}"
            >
              {{#if loading}}
                <span class="jr-spinner"></span>
              {{/if}}

              {{#if !loading}}
                <span>{{label}}</span>
              {{/if}}
            </button>
        """;
    }

}
