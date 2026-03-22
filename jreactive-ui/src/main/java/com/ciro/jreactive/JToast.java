package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Prop;

public class JToast extends HtmlComponent {

    // 🔥 ¡Mira esta belleza! Cero Type<T>, cero $(). Variables crudas.
    @Prop public boolean visible = false;
    @Prop public String message = "";
    @Prop public String variant = "info"; // Valores: success, danger, warning, info
    @Prop public String onClose = "";     // El método del padre que lo cierra

    @Override
    protected String template() {
        return """
            <div style="display: contents;">
                {{#if visible}}
                    <div class="jrx-toast jrx-toast-{{variant}} jrx-slide-in" 
                         client:mount="var self = this; setTimeout(function() { var btn = self.querySelector('.jrx-toast-close'); if(btn) btn.click(); }, 3000)">
                        
                        <div class="jrx-toast-content">
                            <span>{{message}}</span>
                        </div>
                        
                        <button class="jrx-toast-close" @click="{{onClose}}">&times;</button>
                    </div>
                {{/if}}
            </div>
        """;
    }
}