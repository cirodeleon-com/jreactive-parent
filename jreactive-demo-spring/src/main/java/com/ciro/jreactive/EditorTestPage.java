package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.annotations.Client;
import com.ciro.jreactive.annotations.StatefulRam;
import com.ciro.jreactive.router.Route;



@Route(path = "/editor")
@Client
@StatefulRam
public class EditorTestPage extends AppPage {

    // El HTML inicial
    @State
    public String documentHtml = "<p>hola equipo</p><p><br></p><p>tengo ganas de mostrar mi talento</p>";

    @Call
    public void saveDocument() {
        System.out.println("💾 Guardando en Base de Datos: " + documentHtml);
        // Aquí llamarías a tu servicio JPA para guardar
    }

    @Call
    public void clearEditor() {
        this.documentHtml = ""; // Limpiamos desde el servidor
    }

    @Override
    protected String template() {
        return """
            <div class="editor-demo">
                <h1>📝 Editor WYSIWYG (Quill.js)</h1>
                
                <div class="toolbar">
                    <button @click="saveDocument()" class="btn-save">💾 Guardar en DB</button>
                    <button @click="clearEditor()" class="btn-clear">🧹 Limpiar</button>
                </div>

                <textarea name="documentHtml" id="hiddenDoc" style="display:none;"></textarea>

                <div class="editor-wrapper"
                     jrx-ignore
                     data-html="{{documentHtml}}"
                     client:mount="window.QuillDemo.init(this)"
                     client:update="window.QuillDemo.update(this)">
                     
                    <div id="quillContainer"></div>
                </div>
                
                <div class="debug-panel">
                    <h4>HTML crudo en Java:</h4>
                    <code>{{documentHtml}}</code>
                </div>
                
                <p style="margin-top: 20px;"><a data-router href="/">⬅️ Volver</a></p>
            </div>
        """;
    }
}