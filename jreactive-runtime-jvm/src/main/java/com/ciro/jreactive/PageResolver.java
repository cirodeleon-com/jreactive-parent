package com.ciro.jreactive;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import com.ciro.jreactive.router.RouteProvider;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;

public class PageResolver {

    private final RouteProvider registry;

    private record PageKey(String sessionId, String path) {}
    
    // Mantenemos el record para evitar el leak de parámetros huérfanos
    private record PageEntry(HtmlComponent component, Map<String, String> params) {}

    private final Cache<PageKey, PageEntry> cache;

    public PageResolver(RouteProvider registry) {
        this.registry = registry;
        
        this.cache = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES) // La página vive 10 min de inactividad
            .maximumSize(5_000)
            .removalListener((PageKey key, PageEntry entry, RemovalCause cause) -> {
                if (entry != null && entry.component() != null) {
                    entry.component()._unmountRecursive();
                }
            })
            .build();
    }

    public HtmlComponent getPage(String sessionId, String path) {
        PageKey key = new PageKey(sessionId, path);
        
        PageEntry entry = cache.get(key, k -> {
            RouteProvider.Result res = registry.resolve(path);
            HtmlComponent comp = res.component();
            comp._injectParams(res.params());
            return new PageEntry(comp, res.params());
        });

        return entry.component();
    }

    public Map<String, String> getParams(String sessionId, String path) {
        PageEntry entry = cache.getIfPresent(new PageKey(sessionId, path));
        return (entry != null) ? entry.params() : Map.of();
    }

    public HtmlComponent getHomePageInstance(String sessionId) {
        return getPage(sessionId, "/");
    }

    /** * ✅ RESTAURADO: Comportamiento original.
     * Ya no invalidamos la página manualmente al cambiar de ruta.
     * Esto permite que el estado persista si el usuario regresa.
     */
    public void evict(String sessionId, String path) {
        // NO-OP: Dejamos que el Cache expire solo por tiempo de inactividad.
    }

    public void evictAll(String sessionId) {
        cache.asMap().keySet().removeIf(key -> key.sessionId().equals(sessionId));
    }
}