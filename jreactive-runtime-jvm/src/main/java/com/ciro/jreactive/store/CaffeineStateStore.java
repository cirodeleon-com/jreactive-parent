package com.ciro.jreactive.store;

import com.ciro.jreactive.HtmlComponent;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;

import java.util.concurrent.TimeUnit;

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