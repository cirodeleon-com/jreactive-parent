package com.ciro.jreactive;

import static com.ciro.jreactive.Type.$;

public class JCard extends HtmlComponent {

    @Bind public Type<String> title = $(""); 
    @Bind public Type<String> subtitle = $("");

    @Bind public Type<String> variant = $("flat");
    @Bind public Type<Boolean> hoverable = $(Boolean.FALSE);

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

              <div class="jrx-card-body" style="padding: 15px;">
                 <slot />
              </div>
              
              <div class="jrx-card-footer" style="padding: 10px 15px; border-top: 1px solid #eee; background: #fdfdfd; display: flex; gap: 10px; justify-content: flex-end;">
                 <slot name="footer" />
              </div>

            </div>
            """;
    }
}