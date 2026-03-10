package com.ciro.jreactive;

import org.springframework.stereotype.Component;
import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.annotations.StatefulRam;
import com.ciro.jreactive.router.Route;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Route(path = "/power")
@StatefulRam
public class PowerTestPage extends AppPage {

    // 1. DTO del Modelo de Negocio (Ahora incluye el color)
    public static class ServerNode implements Serializable {
        public String id;
        public String name;
        public int cpuUsage;
        public String status;
        public String color; // 🎨 Nuevo campo visual

        public ServerNode() {}
        public ServerNode(String id, String name, int cpuUsage, String status) {
            this.id = id;
            this.name = name;
            this.cpuUsage = cpuUsage;
            this.status = status;
            this.color = cpuUsage > 85 ? "#dc3545" : (cpuUsage > 60 ? "#ffc107" : "#28a745");
        }
    }

    @Bind public List<String> tableHeaders = List.of("ID", "Servidor", "CPU %", "Estado", "Acciones");
    
    @State public List<ServerNode> servers = new ArrayList<>();
    @State public int totalOps = 0;
    @State public ServerNode newServerForm = new ServerNode();

    @Override
    protected void onInit() {
        servers.add(new ServerNode("SRV-1", "Base de Datos Principal", 12, "🟢 Online"));
        servers.add(new ServerNode("SRV-2", "Caché Redis Cluster", 5, "🟢 Online"));
        servers.add(new ServerNode("SRV-3", "Worker Inteligencia Artificial", 88, "🟠 Warning"));
    }

    @Call
    public void stressTest() {
        Random r = new Random();
        for (int i = 0; i < servers.size(); i++) {
            ServerNode s = servers.get(i);
            s.cpuUsage = r.nextInt(100);
            s.status = s.cpuUsage > 85 ? "🔴 Danger" : (s.cpuUsage > 60 ? "🟠 Warning" : "🟢 Online");
            s.color = s.cpuUsage > 85 ? "#dc3545" : (s.cpuUsage > 60 ? "#ffc107" : "#28a745"); // Calculamos el color en Java
            servers.set(i, s); 
        }
        totalOps++;
    }

    @Call
    public void openAddModal() {
        newServerForm = new ServerNode("SRV-" + (servers.size() + 1), "", 0, "🟢 Booting");
        findChild("deployModal", JModal.class).open();
    }

    @Call
    public void saveServer(ServerNode form) {
        servers.add(new ServerNode(form.id, form.name, form.cpuUsage, form.status));
        totalOps++;
        findChild("deployModal", JModal.class).close();
    }

    @Call
    public void deleteServer(ServerNode server) {
        servers.removeIf(s -> s.id.equals(server.id));
        totalOps++;
    }

    @Override
    protected String template() {
        return """
            <div style="padding: 30px; font-family: system-ui, sans-serif; max-width: 900px; margin: 0 auto;">
                <h1 style="border-bottom: 2px solid #eee; padding-bottom: 10px;">🚀 Enterprise Fleet Monitor</h1>
                <p style="color: #666;">Prueba de estrés de JReactive: Tablas anidadas, Modales, y actualizaciones O(1).</p>

                <div style="display: flex; gap: 15px; margin-bottom: 20px;">
                    <JButton label="➕ Desplegar Servidor" type="primary" onClick="openAddModal()" />
                    <JButton label="🔥 Simular Carga (Stress Test)" type="warning" onClick="stressTest()" />
                </div>

                <JCard title="Nodos Activos">
                    <p style="margin-top: -10px; margin-bottom: 20px; font-size: 0.9em; color: #555;">
                        Peticiones procesadas por WS: <strong style="color: #007bff; font-size: 1.2em;">{{totalOps}}</strong>
                    </p>

                    <JTable :columns="tableHeaders" :data="servers" expose="row">
                        <td style="font-family: monospace;">{{row.id}}</td>
                        <td><strong>{{row.name}}</strong></td>
                        <td style="width: 200px;">
                            <div style="width: 100%; background: #e9ecef; border-radius: 4px; overflow: hidden; border: 1px solid #ccc;">
                                <div style="width: {{row.cpuUsage}}%; background: {{row.color}}; color: white; text-align: center; font-size: 12px; font-weight: bold; transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1), background-color 0.3s;">
                                    {{row.cpuUsage}}%
                                </div>
                            </div>
                        </td>
                        <td>{{row.status}}</td>
                        <td>
                            <button @click="deleteServer(row)" style="color: #dc3545; border: 1px solid #dc3545; background: transparent; padding: 5px 10px; border-radius: 4px; cursor: pointer;">
                                🛑 Apagar
                            </button>
                        </td>
                    </JTable>
                </JCard>

                <JModal ref="deployModal" title="Nuevo Nodo de Cómputo">
                    <JForm onSubmit="saveServer(newServerForm)">
                        <input type="hidden" name="newServerForm.id" value="{{newServerForm.id}}">
                        <input type="hidden" name="newServerForm.cpuUsage" value="{{newServerForm.cpuUsage}}">
                        <input type="hidden" name="newServerForm.status" value="{{newServerForm.status}}">
                        
                        <div style="margin-bottom: 15px;">
                            <label style="display: block; font-weight: bold; margin-bottom: 5px;">ID Asignado</label>
                            <input type="text" name="{{newServerForm.id}}" disabled style="width: 100%; padding: 8px; background: #eee; border: 1px solid #ccc; border-radius: 4px;">
                        </div>

                        <JInput :field="newServerForm.name" label="Nombre del Servidor" required="true" placeholder="Ej: Worker de Pagos" />
                    </JForm>
                </JModal>
            </div>
        """;
    }
}