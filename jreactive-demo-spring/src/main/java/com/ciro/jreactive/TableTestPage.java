package com.ciro.jreactive;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.annotations.Client;
import com.ciro.jreactive.annotations.StatefulRam;
import com.ciro.jreactive.router.Route;

@Component
@Route(path = "/table-test")
@StatefulRam
public class TableTestPage extends AppPage {
	
	public static class UserData implements Serializable {
	    public int id;
	    public String name;
	    public String email;

	    public UserData(int id, String name, String email) {
	        this.id = id;
	        this.name = name;
	        this.email = email;
	    }
	}

    @Bind public List<String> headers = List.of("ID", "Nombre", "Email", "Acciones");
    
    @State public List<UserData> users = new ArrayList<>(List.of(
        new UserData(1, "Ciro", "ciro@jreactive.com"),
        new UserData(2, "Compa AI", "bot@jreactive.com")
    ));
    
    @State
    int idCounter = 100;

    @Call
    public void addUser() {
    	idCounter++;
        users.add(new UserData(idCounter, "Nuevo " + idCounter, "user" + idCounter + "@test.com"));
    }

    @Call
    public void deleteUser(UserData user) {
        users.removeIf(u -> u.id == user.id);
    }

    @Override
    protected String template() {
        return """
            <div style="padding: 20px;">
                <h1>📦 Gestión de Usuarios</h1>
                
                <button @click="addUser()" style="margin-bottom: 15px; padding: 10px; background: #28a745; color: white; border: none; border-radius: 4px;">
                    ➕ Agregar Usuario
                </button>

                <JTable :columns="headers" :data="users">
                    <td>{{row.id}}</td>
                    <td><strong>{{row.name}}</strong></td>
                    <td>{{row.email}}</td>
                    <td>
                        <button @click="deleteUser(row)" style="color: red; border: none; background: none; cursor: pointer;">
                            🗑️ Eliminar
                        </button>
                    </td>
                </JTable>

                <p style="margin-top: 15px; color: #666;">Total: {{users.size}} usuarios</p>
            </div>
        """;
    }
}