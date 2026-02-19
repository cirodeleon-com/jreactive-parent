package com.ciro.jreactive.crud;

import java.io.Serializable;
import java.util.UUID;

public class Client_ implements Serializable {
    public String id;
    public String name;
    public String email;
    public String status; // "Activo", "Inactivo"

    // Constructor vacío necesario para Jackson/FST
    public Client_() {} 

    public Client_(String name, String email) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.email = email;
        this.status = "Activo";
    }

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}
    
    
}