package com.ciro.jreactive;

import org.springframework.stereotype.Component;
import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.router.Layout;
import com.ciro.jreactive.router.Route;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
@Route(path = "/delta-test") // Accede en http://localhost:8080/delta-test
public class DeltaTestPage extends AppPage {

    // El framework detectará @State y convertirá esta ArrayList en una SmartList automáticamente
    @State
    public List<String> items = new ArrayList<>();

    // --- ACCIONES QUE GENERAN DELTAS ---

    @Call
    public void addOne() {
        // Genera delta: { "op": "ADD", "index": N, "item": "..." }
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        items.add("Item #" + (items.size() + 1) + " creado a las " + time);
    }

    @Call
    public void removeFirst() {
        if (!items.isEmpty()) {
            // Genera delta: { "op": "REMOVE", "index": 0 }
            items.remove(0);
        }
    }

    @Call
    public void clearAll() {
        // Genera delta: { "op": "CLEAR" }
        items.clear();
    }

    @Call
    public void updateFirst() {
        if (!items.isEmpty()) {
            // 1. Obtenemos el valor actual
            String current = items.get(0);
            
            // 2. Lo modificamos (toggle de un texto)
            String modified;
            if (current.contains(" [EDITADO]")) {
                modified = current.replace(" [EDITADO]", "");
            } else {
                modified = current + " [EDITADO]";
            }

            // 3. 🔥 USAMOS SET: Esto genera delta { "op": "SET", "index": 0, "item": "..." }
            // Gracias a que SmartList.set() ahora intercepta esta llamada.
            items.set(0, modified);
        }
    }

    // --- VISUALIZACIÓN ---

    @Override
    protected String template() {
        return """
            <div style="padding: 20px; font-family: sans-serif; max-width: 600px; margin: 0 auto;">
                <h1 style="border-bottom: 2px solid #eee; padding-bottom: 10px;">
                    🧪 Laboratorio de Deltas
                </h1>
                
                <div style="background: #f8f9fa; padding: 15px; border-radius: 8px; margin-bottom: 20px; border: 1px solid #ddd;">
                    <strong style="display:block; margin-bottom:10px;">Controles de Mutación:</strong>
                    
                    <div style="display: flex; gap: 10px; flex-wrap: wrap;">
                        <button @click="addOne()" 
                                style="cursor:pointer; padding: 8px 12px; background: #28a745; color: white; border: none; border-radius: 4px; font-weight: bold;">
                            ➕ ADD (Agregar)
                        </button>
                        
                        <button @click="updateFirst()" 
                                style="cursor:pointer; padding: 8px 12px; background: #ffc107; color: #212529; border: none; border-radius: 4px; font-weight: bold;">
                            ✏️ SET (Editar 1º)
                        </button>
                        
                        <button @click="removeFirst()" 
                                style="cursor:pointer; padding: 8px 12px; background: #dc3545; color: white; border: none; border-radius: 4px; font-weight: bold;">
                            ➖ REMOVE (Borrar 1º)
                        </button>
                        
                        <button @click="clearAll()" 
                                style="cursor:pointer; padding: 8px 12px; background: #6c757d; color: white; border: none; border-radius: 4px; font-weight: bold;">
                            ❌ CLEAR (Limpiar)
                        </button>
                    </div>
                </div>

                <div style="display: flex; justify-content: space-between; align-items: center;">
                    <h3>Estado de la Lista</h3>
                    <span style="background: #e9ecef; padding: 4px 8px; border-radius: 10px; font-size: 0.85em;">
                        Items: {{items.size}}
                    </span>
                </div>
                
                <ul style="border: 1px solid #ddd; min-height: 100px; padding: 0; list-style: none; border-radius: 4px; overflow: hidden;">
                    {{#each items as item}}
                        <li style="padding: 10px 15px; border-bottom: 1px solid #eee; background: white; transition: background 0.3s;">
                            {{item}}
                        </li>
                    {{/each}}
                </ul>

                <div style="margin-top: 20px; font-size: 0.9em; color: #666; background: #fff3cd; padding: 15px; border-radius: 4px; border-left: 5px solid #ffc107;">
                    💡 <strong>Prueba de Integridad:</strong>
                    <ol style="margin: 5px 0 0 20px;">
                        <li>Abre <b>DevTools > Network > WS</b>.</li>
                        <li>Haz clic en <b>ADD</b>: Deberías ver <code>"op":"ADD"</code>.</li>
                        <li>Haz clic en <b>EDIT 1º</b>: Deberías ver <code>"op":"SET"</code> y solo cambiar el texto del primer elemento.</li>
                        <li>Verifica que la lista NO parpadea (no se repinta entera).</li>
                    </ol>
                </div>
            </div>
        """;
    }
}