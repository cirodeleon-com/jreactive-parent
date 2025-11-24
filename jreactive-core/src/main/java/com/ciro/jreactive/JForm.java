package com.ciro.jreactive;

import static com.ciro.jreactive.Type.$;

public class JForm extends HtmlComponent {

    /** Método que se ejecutará cuando el formulario se envíe. */
    @Bind
    public Type<String> submit = $("");

    /** Si quieres poner un título encima (opcional). */
    @Bind
    public Type<String> title = $("");

    /** Layout principal (vertical, horizontal). */
    @Bind
    public Type<String> layout = $("vertical");

    @Override
    protected String template() {
        return """
            <form class="jrx-form">
              
              {{#if title}}
                <h2 class="jrx-form-title">{{title}}</h2>
              {{/if}}

              <div class="jrx-form-body layout-{{layout}}">
            """ + slot() + """
              </div>

              {{#if submit}}
                <button type="button"
                        @click="{{submit}}">
                  Enviar
                </button>
              {{/if}}

            </form>
            """;
    }
}
