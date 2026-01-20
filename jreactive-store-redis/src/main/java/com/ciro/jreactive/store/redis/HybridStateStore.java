package com.ciro.jreactive.store.redis;

import com.ciro.jreactive.HtmlComponent;
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
        // Lectura siempre es igual: Intentar RAM -> Fallback Redis
        HtmlComponent comp = l1.get(sid, path);
        if (comp != null) return comp;

        comp = l2.get(sid, path);
        if (comp != null) {
            l1.put(sid, path, comp); // Calentar caché (Read-Repair)
        }
        return comp;
    }

    @Override
    public void put(String sid, String path, HtmlComponent comp) {
        if (strongConsistency) {
            // --- MODO ENTERPRISE (Write-Through) ---
            // 1. Escribir en Redis (Fuente de la Verdad) Síncronamente
            // Si falla Redis, falla la operación y el usuario se entera.
            l2.put(sid, path, comp);
            
            // 2. Actualizar RAM
            l1.put(sid, path, comp);
        } else {
            // --- MODO VELOCIDAD (Write-Behind) ---
            // 1. RAM Instantánea
            l1.put(sid, path, comp);
            
            // 2. Redis en segundo plano (Mejor esfuerzo)
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
        if (strongConsistency) {
            // --- MODO ENTERPRISE (Robustez Total) ---
            // 1. Validar versión en Redis primero (Lua Script)
            boolean success = l2.replace(sid, path, comp, expectedVersion);
            
            if (success) {
                // 2. Si Redis aceptó, actualizamos RAM con la nueva versión
                comp._setVersion(expectedVersion + 1);
                l1.put(sid, path, comp);
                return true;
            } else {
                // 3. Conflicto: Invalidamos RAM para forzar recarga fresca
                l1.remove(sid, path);
                return false;
            }
        } else {
            // --- MODO VELOCIDAD (Optimismo Especulativo) ---
            // 1. Asumimos éxito en RAM
            long currentVer = comp._getVersion();
            comp._setVersion(currentVer + 1);
            l1.put(sid, path, comp);

            // 2. Validar en Redis después (Compensación)
            CompletableFuture.runAsync(() -> {
                boolean success = l2.replace(sid, path, comp, currentVer);
                if (!success) {
                    System.out.println("🚨 Conflicto Async Redis detectado. Invalidando RAM.");
                    l1.remove(sid, path);
                }
            });
            return true; // Mentimos temporalmente diciendo que fue exitoso
        }
    }

    @Override
    public void remove(String sid, String path) {
        if (strongConsistency) {
            l2.remove(sid, path);
            l1.remove(sid, path);
        } else {
            l1.remove(sid, path);
            CompletableFuture.runAsync(() -> l2.remove(sid, path));
        }
    }

    @Override
    public void removeSession(String sid) {
        // La limpieza de sesión suele ser segura de hacer async en ambos modos
        // para no bloquear el Logout, pero si quieres ser estricto:
        l1.removeSession(sid);
        CompletableFuture.runAsync(() -> l2.removeSession(sid));
    }
}