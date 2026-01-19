package com.ciro.jreactive;

import com.ciro.jreactive.router.RouteProvider;
import com.ciro.jreactive.store.StateStore;
import java.util.Map;

public class PageResolver {

    private final RouteProvider registry;
    private final StateStore store; // 🔥 Inyección de dependencia (Interfaz)

    public PageResolver(RouteProvider registry, StateStore store) {
        this.registry = registry;
        this.store = store;
    }

    public HtmlComponent getPage(String sessionId, String path) {
        // 1. Intentar obtener del store (RAM, Redis, etc.)
        HtmlComponent comp = store.get(sessionId, path);

        // 2. Si no existe (Cache Miss), crear nueva instancia
        if (comp == null) {
            RouteProvider.Result res = registry.resolve(path);
            comp = res.component();
            comp._injectParams(res.params());
            
            String stableId = generateStableIdFromPath(path);
            comp.setId(stableId);
            
            // 3. Guardar en el store
            store.put(sessionId, path, comp);
        }
        return comp;
    }
    
    private String generateStableIdFromPath(String path) {
        if (path == null || path.equals("/") || path.isBlank()) {
            return "page_index";
        }
        // Reemplazamos barras y caracteres raros por guiones bajos
        String safeId = path.replaceAll("[^a-zA-Z0-9]", "_");
        
        // Quitamos guiones bajos al inicio/final si quedan
        if (safeId.startsWith("_")) safeId = safeId.substring(1);
        if (safeId.endsWith("_")) safeId = safeId.substring(0, safeId.length() - 1);
        
        return "page_" + safeId;
    }

    public Map<String, String> getParams(String sessionId, String path) {
        // Optimizacion: En lugar de guardar los params en el store (que complica la serialización),
        // simplemente volvemos a resolver la ruta. Es muy rápido y stateless.
        RouteProvider.Result res = registry.resolve(path);
        return res.params();
    }

    public HtmlComponent getHomePageInstance(String sessionId) {
        return getPage(sessionId, "/");
    }

    /**
     * Método vital para arquitecturas distribuidas (Redis).
     * Debe llamarse al final de un request para asegurar que los cambios se guarden.
     */
    public void persist(String sessionId, String path, HtmlComponent comp) {
        store.put(sessionId, path, comp);
    }

    public void evict(String sessionId, String path) {
        store.remove(sessionId, path);
    }

    public void evictAll(String sessionId) {
        store.removeSession(sessionId);
    }
}