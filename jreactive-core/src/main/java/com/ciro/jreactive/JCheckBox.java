/* === File: jreactive-core/src/main/java/com/ciro/jreactive/JCheckBox.java === */
package com.ciro.jreactive;

import static com.ciro.jreactive.Type.$;

/**
 * Checkbox básico de JReactive.
 *
 * Uso típico:
 *
 *   @State
 *   Boolean acceptTerms = false;
 *
 *   ...
 *
 *   <JCheckBox
 *     field="form.acceptTerms"
 *     :checked="acceptTerms"
 *     label="Acepto los términos y condiciones"
 *     :required="true"
 *     helpText="Debes aceptar para continuar"
 *   />
 *
 * - "field" va al name del <input> → form.acceptTerms para Bean Validation.
 * - ":checked" puede enlazarse a un @State/@Bind plano (sin puntos).
 */
public class JCheckBox extends HtmlComponent {

    /** Nombre completo del campo: ej. "form.acceptTerms". */
    @Bind
    public Type<String> field = $("");

    /** Texto visible junto al checkbox. */
    @Bind
    public Type<String> label = $("");

    /** ¿Es requerido? (solo visual: muestra *). */
    @Bind
    public Type<Boolean> required = $(Boolean.FALSE);

    /** ¿Está deshabilitado? (por ahora solo visual). */
    @Bind
    public Type<Boolean> disabled = $(Boolean.FALSE);

    /** Estado marcado inicial/controlado. Se puede enlazar con :checked="..." */
    @Bind
    public Type<Boolean> checked = $(Boolean.FALSE);

    /** Texto de ayuda debajo. */
    @Bind
    public Type<String> helpText = $("");

    public JCheckBox() {}

    @Override
    protected String template() {
        return """
            <div class="jrx-field jrx-checkbox">
              <label>
                {{#if checked}}
                  <input
                    type="checkbox"
                    name="{{field}}"
                    checked
                  />
                {{else}}
                  <input
                    type="checkbox"
                    name="{{field}}"
                  />
                {{/if}}

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
