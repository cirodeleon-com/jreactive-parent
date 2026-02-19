package com.ciro.jreactive.crud;


import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ClientService {
    // DB Simulada
    private final Map<String, Client_> db = new ConcurrentHashMap<>();

    public List<Client_> findAll() {
        return new ArrayList<>(db.values());
    }

    public void save(Client_ client) {
        if (client.id == null || client.id.isBlank()) {
            client.id = UUID.randomUUID().toString();
        }
        db.put(client.id, client);
    }

    public void delete(String id) {
        db.remove(id);
    }
}
