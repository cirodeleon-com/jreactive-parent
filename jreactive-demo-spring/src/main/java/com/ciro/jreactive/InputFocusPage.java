package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.annotations.Client; // 🔥 IMPORTANTE: Activa el Proxy O(1)
import com.ciro.jreactive.annotations.Stateful;
import com.ciro.jreactive.annotations.StatefulRam;
import com.ciro.jreactive.annotations.Stateless;
import com.ciro.jreactive.router.Route;
import org.springframework.stereotype.Component;


@Route(path = "/focus-test")
@StatefulRam
@Client
public class InputFocusPage extends AppPage {

    @State
    public int count = 0;

    @State
    public String typedText = "";

    @Call
    public void increment() {
        count++;
        // Al modificar 'count', el backend enviará el delta.
        // El frontend recibirá { count: N } y el Proxy debería actualizar SOLO el número.
    }

    @Override
    protected String template() {
        return """
            <div style="padding: 40px; font-family: sans-serif; max-width: 600px; margin: 0 auto;">
                <h1>🕵️ Prueba de Foco (Proxy O(1))</h1>
                
                <div style="background: #e3f2fd; padding: 20px; border-radius: 8px; margin-bottom: 20px; border: 1px solid #90caf9;">
                    <h3>Contador: <span style="font-size: 1.5em; font-weight: bold; color: #d32f2f;">{{count}}</span></h3>
                    <p>Texto en memoria: {{typedText}}</p>
                </div>

                <div style="margin-bottom: 20px;">
                    <label style="display: block; margin-bottom: 8px; font-weight: bold;">Tu Input de Prueba:</label>
                    
                    <input type="text" 
                           name="typedText" 
                           placeholder="Escribe aquí y NO salgas..." 
                           style="width: 100%; padding: 10px; font-size: 1.2em; border: 2px solid #333; border-radius: 4px;"
                    />
                </div>

                <button @click="increment()" 
                        onmousedown="event.preventDefault()"
                        style="padding: 15px 30px; background: #28a745; color: white; border: none; border-radius: 4px; font-size: 1.2em; cursor: pointer; width: 100%;">
                    💣 Incrementar (Sin robar foco)
                </button>
                
                <div style="margin-top: 30px; color: #555; background: #f8f9fa; padding: 15px; border-radius: 4px; font-size: 0.9em;">
                    <strong>Cómo realizar la prueba:</strong>
                    <ol>
                        <li>Haz clic dentro del <b>Input</b>.</li>
                        <li>Escribe algo y <b>mantén el cursor ahí</b> (parpadeando).</li>
                        <li>Haz clic muchas veces en el botón verde <b>INCREMENTAR</b>.</li>
                    </ol>
                    <p>
                        🟢 <b>ÉXITO (O(1)):</b> El número cambia, pero tú puedes seguir escribiendo sin interrupciones. El input NO parpadea.<br>
                        🔴 <b>FALLO (innerHTML):</b> El input pierde el foco, el cursor desaparece o el texto seleccionado se resetea.
                    </p>
                </div>
            </div>
        """;
    }
}