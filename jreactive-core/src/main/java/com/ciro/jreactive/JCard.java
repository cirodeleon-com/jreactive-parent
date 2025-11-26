package com.ciro.jreactive;

import static com.ciro.jreactive.Type.$;

/**
 * Componente de tarjeta contenedora para JReactive.
 *
 * Uso típico:
 *
 *   <JCard
 *     :title="pageTitle"
 *     :subtitle="pageSubtitle"
 *     variant="elevated"
 *   >
 *     <!-- aquí va el contenido interno -->
 *     <JForm ...>...</JForm>
 *   </JCard>
 *
 * El contenido interno se inserta vía slot() en el body.
 */
public class JCard extends HtmlComponent {

    /** Título principal de la tarjeta (opcional). */
    @Bind
    public Type<String> title = $("");

    /** Subtítulo o descripción corta (opcional). */
    @Bind
    public Type<String> subtitle = $("");

    /**
     * Variante visual (para CSS futuro): 
     *  - "flat" (por defecto)
     *  - "elevated"
     *  - "outlined"
     */
    @Bind
    public Type<String> variant = $("flat");

    /** Si quieres marcar la tarjeta como clickable / hoverable (para CSS). */
    @Bind
    public Type<Boolean> hoverable = $(Boolean.FALSE);

    @Override
    protected String template() {
        return """
            <div class="jrx-card jrx-card-{{variant}}">
              
              {{#if title}}
                <div class="jrx-card-header">
                  <h2 class="jrx-card-title">{{title}}</h2>
                  
                  {{#if subtitle}}
                    <p class="jrx-card-subtitle">{{subtitle}}</p>
                  {{/if}}
                </div>
              {{/if}}

              <div class="jrx-card-body">
            """ + slot() + """
              </div>

            </div>
            """;
    }
}
