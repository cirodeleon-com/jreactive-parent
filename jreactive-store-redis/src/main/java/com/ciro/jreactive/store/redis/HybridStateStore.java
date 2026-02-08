package com.ciro.jreactive.store.redis;

import com.ciro.jreactive.HtmlComponent;
import com.ciro.jreactive.annotations.Stateless; // 👈 Importante
import com.ciro.jreactive.store.StateStore;

import java.util.concurrent.CompletableFuture;

public class HybridStateStore implements StateStore {

    private final StateStore l1; // Caffeine (RAM)
    private final StateStore l2; // Redis (Persistente)
    private final boolean strongConsistency; // Configurable

    public HybridStateStore(StateStore l1, StateStore l2, boolean strongConsistency) {
        this.l1 = l1;
        this.l2 = l2;
        this.strongConsistency = strongConsistency;
    }

    @Override
    public HtmlComponent get(String sid, String path) {
        // Lectura: Intentar RAM -> Fallback Redis
        HtmlComponent comp = l1.get(sid, path);
        if (comp != null) return comp;

        // Si no está en RAM, buscamos en Redis (por si hubo reinicio)
        // Nota: Un componente @Stateless no estará en Redis, así que devolverá null correctamente.
        comp = l2.get(sid, path);
        if (comp != null) {
            l1.put(sid, path, comp); // Read-Repair
        }
        return comp;
    }

    @Override
    public void put(String sid, String path, HtmlComponent comp) {
        // 1. Siempre escribir en RAM (Velocidad inmediata)
        l1.put(sid, path, comp);

        // 2. 🔥 CHECK STATELESS: Si el componente es efímero, NO tocamos Redis
        if (comp.getClass().isAnnotationPresent(Stateless.class)) {
            return; // 🚀 Salida temprana: Ahorro de IO y Serialización
        }

        // 3. Si es persistente, procedemos con la estrategia elegida
        if (strongConsistency) {
            l2.put(sid, path, comp);
        } else {
            CompletableFuture.runAsync(() -> {
                try {
                    l2.put(sid, path, comp);
                } catch (Exception e) {
                    System.err.println("🔥 Fallo Async Redis PUT: " + e.getMessage());
                }
            });
        }
    }

    @Override
    public boolean replace(String sid, String path, HtmlComponent comp, long expectedVersion) {
        
        // 1. 🔥 CHECK STATELESS: Gestión de concurrencia solo en RAM
        if (comp.getClass().isAnnotationPresent(Stateless.class)) {
            // Delegamos la atomicidad a Caffeine (que usa Atomic/Compute)
            // No necesitamos Script Lua ni red.
            return l1.replace(sid, path, comp, expectedVersion);
        }

        // 2. Componentes Persistentes (Lógica original)
        if (strongConsistency) {
            // --- MODO ENTERPRISE ---
            boolean success = l2.replace(sid, path, comp, expectedVersion);
            if (success) {
                comp._setVersion(expectedVersion + 1);
                l1.put(sid, path, comp);
                return true;
            } else {
                l1.remove(sid, path); // Invalidar cache sucio
                return false;
            }
        } else {
            // --- MODO VELOCIDAD ---
            long currentVer = comp._getVersion();
            comp._setVersion(currentVer + 1);
            l1.put(sid, path, comp);

            CompletableFuture.runAsync(() -> {
                boolean success = l2.replace(sid, path, comp, currentVer);
                if (!success) {
                    System.out.println("🚨 Conflicto Async Redis detectado. Invalidando RAM.");
                    l1.remove(sid, path);
                }
            });
            return true;
        }
    }

    @Override
    public void remove(String sid, String path) {
        // Borramos de ambos por seguridad. 
        // Si era Stateless, el delete en Redis retornará 0 (no pasa nada).
        // No vale la pena instanciar el objeto para ver si tiene la anotación antes de borrar.
        l1.remove(sid, path);
        
        if (strongConsistency) {
            l2.remove(sid, path);
        } else {
            CompletableFuture.runAsync(() -> l2.remove(sid, path));
        }
    }

    @Override
    public void removeSession(String sid) {
        l1.removeSession(sid);
        CompletableFuture.runAsync(() -> l2.removeSession(sid));
    }
}