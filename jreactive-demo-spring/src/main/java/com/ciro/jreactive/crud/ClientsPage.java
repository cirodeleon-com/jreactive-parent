package com.ciro.jreactive.crud;



import com.ciro.jreactive.*;
import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.annotations.Stateless;
import com.ciro.jreactive.router.Route;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Route(path = "/clients")
@Stateless
public class ClientsPage extends AppPage {

    @Autowired
    private transient ClientService service;

    // --- ESTADO ---
    @State public List<Client> clients = new ArrayList<>();
    @State public Client form = new Client(); // Objeto para el formulario
    @State public boolean isEditing = false;
    @State public String modalTitle = "Nuevo Cliente";
    
    // Configuración tabla
    @Bind public List<String> headers = List.of("Nombre", "Email", "Estado", "Acciones");
    @State public List<JSelect.Option> statusOptions = new ArrayList<>();

    // --- CICLO DE VIDA ---
    @Override
    public void onInit() {
        // Carga inicial (Solo ocurre 1 vez por sesión)
        this.clients = service.findAll();
        
        this.statusOptions.add(new JSelect.Option("Activo", "Activo"));
        this.statusOptions.add(new JSelect.Option("Inactivo", "Inactivo"));
        
    }

    // --- ACCIONES (@Call) ---

    @Call
    public void openCreate() {
        this.form = new Client(); // Limpiar form
        this.isEditing = false;
        // Abrimos el modal por referencia (usando el ID del componente hijo)
        
        this.modalTitle = "Crear Cliente";
        findChild("clientModal", JModal.class).open();
    }

    @Call
    public void openEdit(Client row) {
        // Clonamos para no editar la tabla en tiempo real antes de guardar
        this.form = new Client(); 
        this.form.setId(row.getId());
        this.form.name = row.name;
        this.form.email = row.email;
        this.form.status = row.status;
        
        this.isEditing = true;
        
        this.modalTitle = "Editar Cliente: " + row.name;
        
        
        
        findChild("clientModal", JModal.class).open();
    }

    @Call
    public void save(Client form) {
        // 1. Guardar en DB
        service.save(form);
        
        // 2. Refrescar lista en memoria
        this.clients = service.findAll();
        
        // 3. Cerrar modal
        findChild("clientModal", JModal.class).close();
    }

    @Call
    public void delete(Client row) {
        service.delete(row.id);
        this.clients = service.findAll();
    }

    // --- VISTA (Template) ---
    @Override
    protected String template() {
        return """
            <div style="padding: 20px;">
                <h1>Gestión de Clientes</h1>

                <div style="margin-bottom: 15px;">
                    <JButton label="Nuevo Cliente" type="primary" onClick="openCreate()" />
                </div>

                <JTable :columns="headers" :data="clients">
                    <td>{{row.name}}</td>
                    <td>{{row.email}}</td>
                    <td>
                        <span class="badge {{row.status}}">{{row.status}}</span>
                    </td>
                    <td>
                        <button @click="openEdit(row)">✏️</button>
                        <button @click="delete(row)" style="color:red">🗑️</button>
                    </td>
                </JTable>

                <JModal ref="clientModal" :title="modalTitle">
                    
                    <JForm onSubmit="save(form)">

                        <input type="hidden" name="form.id" value="{{form.id}}">                        
                    
                        <JInput 
                            label="Nombre Completo" 
                            :field="form.name" 
                            required="true" 
                        />
                        
                        <JInput 
                            label="Correo Electrónico" 
                            type="email"
                            :field="form.email" 
                            required="true" 
                        />

                        <JSelect 
                            label="Estado"
                            :field="form.status"
                            :options="statusOptions"
                            required="true"
                            placeholder="Selecciona una opcion"
                        />

                    </JForm>

                </JModal>
            </div>
        """;
    }
}
