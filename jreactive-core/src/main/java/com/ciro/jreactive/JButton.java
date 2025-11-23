package com.ciro.jreactive;

public class JButton extends HtmlComponent {

    // Props reactivas
    @Bind public String label = "Botón";
    @Bind public String type = "primary";  // primary | secondary | danger
    @Bind public boolean loading = false;
    @Bind public boolean disabled = false;

    @Override
    protected String template() {
        return """
            <button 
                class="jr-btn {{type}}"
                :disabled="{{disabled}} || {{loading}}"
                @click="onClick()"
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

    // Método para enganchar desde el parent:
    public void onClick() {
        // vacío — será interceptado por el namespacing del padre
    }
}
