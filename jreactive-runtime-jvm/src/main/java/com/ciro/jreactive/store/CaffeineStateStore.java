package com.ciro.jreactive.store;

import com.ciro.jreactive.HtmlComponent;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class CaffeineStateStore implements StateStore {

    private final Cache<String, HtmlComponent> cache;

    public CaffeineStateStore() {
        this.cache = Caffeine.newBuilder()
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .maximumSize(10_000)
                .removalListener((String key, HtmlComponent val, RemovalCause cause) -> {
                    // Limpieza automática al expirar (Integridad de Memoria)
                    if (val != null) val._unmountRecursive();
                })
                .build();
    }

    // Clave compuesta para evitar colisiones entre sesiones
    private String k(String sid, String path) { 
        return sid + "::" + path; 
    }

    @Override
    public HtmlComponent get(String sid, String path) {
        return cache.getIfPresent(k(sid, path));
    }

    @Override
    public void put(String sid, String path, HtmlComponent comp) {
        cache.put(k(sid, path), comp);
    }
    
    // 🔥 IMPLEMENTACIÓN DE OPTIMISTIC LOCKING EN MEMORIA
    @Override
    public boolean replace(String sid, String path, HtmlComponent newComp, long expectedVersion) {
        String key = k(sid, path);
        AtomicBoolean success = new AtomicBoolean(false);

        // 'compute' es atómico por clave en ConcurrentMap
        cache.asMap().compute(key, (k, currentComp) -> {
            // Caso 1: No existe (Creación)
            if (currentComp == null) {
                if (expectedVersion == 0) {
                    newComp._setVersion(1);
                    success.set(true);
                    return newComp;
                } else {
                    return null; // Fallo: esperábamos actualización pero no existe
                }
            }

            // Caso 2: Existe (Actualización)
            if (currentComp._getVersion() == expectedVersion) {
                // Éxito: Versiones coinciden
                newComp._setVersion(expectedVersion + 1);
                success.set(true);
                return newComp;
            } else {
                // Fallo: Alguien más lo modificó
                return currentComp; // Mantenemos el viejo sin cambios
            }
        });

        return success.get();
    }

    @Override
    public void remove(String sid, String path) {
        cache.invalidate(k(sid, path));
    }

    @Override
    public void removeSession(String sid) {
        // Caffeine no es ideal para borrar por prefijo, pero en memoria es rápido.
        // Esto se ejecuta en O(n) sobre las claves presentes.
        cache.asMap().keySet().removeIf(key -> key.startsWith(sid + "::"));
    }
}