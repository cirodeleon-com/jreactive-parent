package com.ciro.jreactive;

import static com.ciro.jreactive.Type.$;

public class JFileUpload extends HtmlComponent {

    @Bind public Type<String> field = $("");
    @Bind public Type<String> label = $("Subir Archivo");
    
    // 🔥 EVENTOS EXPUESTOS AL DEVELOPER
    @Bind public String onChange = "";
    @Bind public String onClick = "";  // <-- Añadimos soporte para el click

    @Override
    protected String template() {
        return """
            <div class="jrx-fileupload" style="margin-bottom: 15px;" 
                 client:mount="
                    const input = this.querySelector('input');
                    const progressBox = this.querySelector('.jrx-progress-box');
                    const bar = this.querySelector('.jrx-progress-bar');
                    const text = this.querySelector('.jrx-progress-text');
                    
                    input.addEventListener('jrx:upload:progress', (e) => {
                        progressBox.style.display = 'block';
                        bar.style.width = e.detail.percent + '%';
                        text.innerText = 'Subiendo... ' + e.detail.percent + '%';
                        
                        if (e.detail.percent === 100) {
                            text.innerText = '¡Procesando!';
                            bar.style.background = '#28a745';
                            setTimeout(() => { 
                                progressBox.style.display = 'none'; 
                                bar.style.width = '0%'; 
                                bar.style.background = '#007bff'; 
                            }, 2000);
                        }
                    });
                 ">
                
                <label style="display: block; font-weight: bold; margin-bottom: 8px;">{{label}}</label>
                
                <input type="file" name="{{field}}" 
                       @change="{{onChange}}" 
                       @click="{{onClick}}" 
                       style="display: block; width: 100%; padding: 10px; border: 2px dashed #ccc; border-radius: 6px; cursor: pointer; background: #fdfdfd;" />
                
                <div class="jrx-progress-box" style="display: none; margin-top: 10px;">
                    <div style="display: flex; justify-content: space-between; font-size: 12px; color: #666; margin-bottom: 4px;">
                        <span class="jrx-progress-text">Subiendo...</span>
                    </div>
                    <div style="width: 100%; background: #eee; border-radius: 4px; height: 8px; overflow: hidden;">
                        <div class="jrx-progress-bar" style="width: 0%; height: 100%; background: #007bff; transition: width 0.1s linear;"></div>
                    </div>
                </div>
            </div>
        """;
    }
}