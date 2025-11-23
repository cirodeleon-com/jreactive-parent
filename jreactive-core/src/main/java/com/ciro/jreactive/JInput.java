/* === Module: jreactive-core | File: jreactive-core/src/main/java/com/ciro/jreactive/components/JInput.java === */
package com.ciro.jreactive;

import static com.ciro.jreactive.Type.$;

/**
 * Componente base de input de formulario para JReactive.
 *
 * Pensado para usarse como:
 *
 *   <JInput
 *     field="form.email"
 *     label="Correo"
 *     type="email"
 *     placeholder="correo@ejemplo.com"
 *   />
 *
 * El name del <input> será exactamente el valor de "field",
 * para encajar con Bean Validation y applyValidationErrors().
 */
public class JInput extends HtmlComponent {

    /** Nombre completo del campo: ej. "form.email", "user.name". */
    @Bind
    public Type<String> field = $("");

    /** Texto visible de la etiqueta. */
    @Bind
    public Type<String> label = $("");

    /** Tipo HTML: text, email, password, number, date, etc. */
    @Bind
    public Type<String> type = $("text");

    /** Placeholder del input. */
    @Bind
    public Type<String> placeholder = $("");

    /** Marca si es requerido (añade required y un asterisco). */
    @Bind
    public Type<Boolean> required = $(Boolean.FALSE);

    /** Deshabilitado (disabled). */
    @Bind
    public Type<Boolean> disabled = $(Boolean.FALSE);

    /** Texto de ayuda debajo del input. */
    @Bind
    public Type<String> helpText = $("");

    /** Valor de autocomplete: email, name, new-password, etc. */
    @Bind
    public Type<String> autocomplete = $("");

    public JInput() {
        // defaults ya están inicializados arriba
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

                <input
                  type="{{type}}"
                  name="{{field}}"
                  placeholder="{{placeholder}}"
                  autocomplete="{{autocomplete}}"
                />
              </label>

              {{#if helpText}}
                <small class="jrx-help">{{helpText}}</small>
              {{/if}}
            </div>
            """;
    }

}
