package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Call;

public class JModal extends HtmlComponent {

    @State public boolean visible = false;
    @Bind public String title = "Mensaje del Sistema";

    @Call
    public void open() {
        this.visible = true;
    }

    @Call
    public void close() {
        this.visible = false;
    }

    @Override
    protected String template() {
        return """
            {{#if visible}}
                <div class="jrx-modal-backdrop" 
                     client:mount="document.body.style.overflow='hidden'" 
                     client:unmount="document.body.style.overflow=''">
                    
                    <div class="jrx-modal-content">
                        <div class="jrx-modal-header">
                            <strong>{{title}}</strong>
                            <button @click="close()" style="border:none; background:none; cursor:pointer; font-size:20px;">&times;</button>
                        </div>
                        <div class="jrx-modal-body">
                            <slot />
                        </div>
                    </div>
                </div>
            {{/if}}
        """;
    }
}