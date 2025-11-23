package com.ciro.jreactive;

import static com.ciro.jreactive.Type.$;

/**
 * Componente base de formulario para JReactive.
 *
 * Uso esperado:
 *
 *   <JForm @submit="register(form)">
 *      <JInput ... />
 *      <JButton ... />
 *   </JForm>
 *
 * El @submit se traduce a un @click interno del form.
 */
public class JForm extends HtmlComponent {

    /** Método que se ejecutará cuando el formulario se envíe. */
    @Bind
    public Type<String> submit = $("");

    /** Si quieres poner un título encima (opcional). */
    @Bind
    public Type<String> title = $("");

    /** Layout principal (vertical, horizontal). */
    @Bind
    public Type<String> layout = $("vertical"); // futuro: row/column

    public JForm() {}

    @Override
    protected String template() {
        return """
            <form class="jrx-form">
              
              {{#if title}}
                <h2 class="jrx-form-title">{{title}}</h2>
              {{/if}}

              <div class="jrx-form-body layout-{{layout}}">
                {{children}}
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

