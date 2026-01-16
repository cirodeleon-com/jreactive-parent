package com.ciro.jreactive.store.redis;

import com.ciro.jreactive.HtmlComponent;
import com.ciro.jreactive.store.StateStore;

import java.util.concurrent.CompletableFuture;

public class HybridStateStore implements StateStore {

    private final StateStore l1; // Caffeine (RAM)
    private final StateStore l2; // Redis (Persistente)

    public HybridStateStore(StateStore l1, StateStore l2) {
        this.l1 = l1;
        this.l2 = l2;
    }

    @Override
    public HtmlComponent get(String sid, String path) {
        // 1. Velocidad de la luz (RAM)
        HtmlComponent comp = l1.get(sid, path);
        if (comp != null) return comp;

        // 2. Fallback a Redis (si el usuario cambió de servidor)
        comp = l2.get(sid, path);
        
        // 3. Si lo encontramos en Redis, lo subimos a RAM ("calentamos caché")
        if (comp != null) {
            l1.put(sid, path, comp);
        }
        return comp;
    }

    @Override
    public void put(String sid, String path, HtmlComponent comp) {
        // 1. Escritura Síncrona en RAM (Feedback instantáneo al usuario)
        l1.put(sid, path, comp);

        // 2. Escritura Asíncrona en Redis (Backup en background)
        // Esto hace que el usuario NO sienta la latencia de Redis
        CompletableFuture.runAsync(() -> {
            try {
                l2.put(sid, path, comp);
            } catch (Exception e) {
                System.err.println("Fallo sincronizando Redis: " + e.getMessage());
            }
        });
    }

    @Override
    public void remove(String sid, String path) {
        l1.remove(sid, path);
        CompletableFuture.runAsync(() -> l2.remove(sid, path));
    }
    
    @Override 
    public void removeSession(String sid) {
        l1.removeSession(sid);
        l2.removeSession(sid);
    }
}