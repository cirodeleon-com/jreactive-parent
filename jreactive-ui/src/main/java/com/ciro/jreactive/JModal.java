package com.ciro.jreactive;

public class JModal extends HtmlComponent {

    // 🔥 Ahora son @Bind. El padre se los inyecta.
    @Bind public boolean visible = false;
    @Bind public String title = "Mensaje del Sistema";
    @Bind public String onClose = ""; // Evento para avisarle al padre que lo cierre

    @Override
    protected String template() {
        return """
          <div style="display: contents;">		
            {{#if visible}}
                <div class="jrx-modal-backdrop" 
                     client:mount="document.body.style.overflow='hidden'" 
                     client:unmount="document.body.style.overflow=''">
                    
                    <div class="jrx-modal-content">
                        <div class="jrx-modal-header">
                            <strong>{{title}}</strong>
                            <button @click="{{onClose}}" style="border:none; background:none; cursor:pointer; font-size:20px;">&times;</button>
                        </div>
                        <div class="jrx-modal-body">
                            <slot />
                        </div>
                    </div>
                </div>
            {{/if}}
           </div>
        """;
    }
}