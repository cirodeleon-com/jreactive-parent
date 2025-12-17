package com.ciro.jreactive;

import static com.ciro.jreactive.Type.$;

public class JForm extends HtmlComponent {

    @Bind public Type<String> title = $(""); 
    @Bind public Type<String> layout = $("vertical");

    // submit (puede venir vacío)
    @Bind public String onSubmit = "";

    @Override
    protected String template() {
        return """
            <form class="jrx-form" @submit="{{onSubmit}}" novalidate>

              {{#if title}}
                <h2 class="jrx-form-title">{{title}}</h2>
              {{/if}}

              <div class="jrx-form-body layout-{{layout}}">
            """ + slot() + """
              </div>

              {{#if onSubmit}}
                <button type="submit">Enviar</button>
              {{/if}}

            </form>
        """;
    }
}
