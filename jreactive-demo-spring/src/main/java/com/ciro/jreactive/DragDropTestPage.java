package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.annotations.Client;
import com.ciro.jreactive.annotations.StatefulRam;
import com.ciro.jreactive.router.Route;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;


@Route(path = "/kanban")
@Client
@StatefulRam
public class DragDropTestPage extends AppPage {

    // Nuestra lista reactiva
    @State
    public List<String> tasks = new ArrayList<>(List.of(
        "1️⃣ Diseñar la base de datos",
        "2️⃣ Configurar Spring Boot",
        "3️⃣ Crear componentes UI",
        "4️⃣ Implementar seguridad",
        "5️⃣ Desplegar a producción"
    ));

    // Variables ocultas que JS llenará
    @State public int dragOld = 0;
    @State public int dragNew = 0;

    @Call
    public void reorderTask(int oldIndex, int newIndex) {
        if (oldIndex == newIndex || oldIndex < 0 || newIndex < 0 || oldIndex >= tasks.size() || newIndex >= tasks.size()) {
            return; // Seguridad: Si lo soltó en el mismo sitio o fuera de rango, no hacemos nada
        }
        
        // Magia Java: Extraemos de la posición vieja y lo insertamos en la nueva
        String task = tasks.remove(oldIndex);
        tasks.add(newIndex, task);
        
        System.out.println("🔄 JReactive: Tarea movida de " + oldIndex + " a " + newIndex + " -> " + task);
    }

    @Override
    protected String template() {
        return """
            <div class="kanban-demo">
                <h1>🏗️ Drag & Drop (SortableJS)</h1>
                <p>Arrastra las tareas. JS actualiza el DOM y JReactive sincroniza el backend silenciosamente.</p>

                <div style="display: none;">
                    <input type="hidden" name="dragOld" id="dragOld" />
                    <input type="hidden" name="dragNew" id="dragNew" />
                    <button id="btnReorder" @click="reorderTask(dragOld, dragNew)"></button>
                </div>

                <div class="board">
                    <div class="column">
                        <h3>📌 Tareas Pendientes</h3>
                        <ul class="sortable-list" jrx-ignore client:mount="window.SortableDemo.init(this)">
                            {{#each tasks as task}}
                                <li class="task-card">
                                    <span class="drag-handle">☰</span>
                                    {{task}}
                                </li>
                            {{/each}}
                        </ul>
                    </div>
                </div>
                
                <div class="debug-panel">
                    <h4>Estado real en el Servidor (SmartList):</h4>
                    <ul>
                        {{#each tasks as debugTask}}
                            <li><small>{{debugTask}}</small></li>
                        {{/each}}
                    </ul>
                </div>
                
                <p style="margin-top: 20px;"><a data-router href="/">⬅️ Volver</a></p>
            </div>
        """;
    }
}