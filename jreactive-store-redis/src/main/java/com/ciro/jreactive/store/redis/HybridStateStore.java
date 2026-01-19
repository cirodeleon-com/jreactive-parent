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
        CompletableFuture.runAsync(() -> {
            try {
                l2.put(sid, path, comp);
            } catch (Exception e) {
                System.err.println("Fallo sincronizando Redis (PUT): " + e.getMessage());
            }
        });
    }

    @Override
    public void remove(String sid, String path) {
        l1.remove(sid, path);
        
        // Async remove en L2
        CompletableFuture.runAsync(() -> {
            try {
                l2.remove(sid, path);
            } catch (Exception e) {
                System.err.println("Fallo sincronizando Redis (REMOVE): " + e.getMessage());
            }
        });
    }
    
    @Override 
    public void removeSession(String sid) {
        // 1. Limpieza inmediata en RAM
        l1.removeSession(sid);
        
        // 2. Limpieza asíncrona en Redis (Fire & Forget)
        // 🔥 MEJORA: No bloqueamos el hilo principal esperando a Redis
        CompletableFuture.runAsync(() -> {
            try {
                l2.removeSession(sid);
            } catch (Exception e) {
                System.err.println("Fallo limpiando sesión en Redis: " + e.getMessage());
            }
        });
    }
}