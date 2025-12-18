package com.ciro.jreactive;

import com.ciro.jreactive.router.RouteRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

// ✅ Imports para Caffeine Cache
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;

@Component
public class PageResolver {

    private final RouteRegistry registry;

    /** Clave compuesta: sesión + path concreto ("/users/42") */
    private record PageKey(String sessionId, String path) {}

    /** * ✅ FIX MULTI-TAB: Cache inteligente en lugar de Mapa simple.
     * Guarda las instancias de páginas vivas.
     */
    private final Cache<PageKey, HtmlComponent> instances;

    /** Parámetros extraídos del path, también por (sessionId, path) */
    private final Map<PageKey, Map<String,String>> paramsByPage = new ConcurrentHashMap<>();

    public PageResolver(RouteRegistry registry) {
        this.registry = registry;
        
        // Configuración de la Cache
        this.instances = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES) // 1. Se mantiene viva 10 min tras el último uso
            .maximumSize(5_000)                      // 2. Límite de seguridad
            // 3. Listener de limpieza automática:
            .removalListener((PageKey key, HtmlComponent comp, RemovalCause cause) -> {
                // Limpiamos los parámetros asociados
                paramsByPage.remove(key);
                
                // Desmontamos el componente correctamente para liberar recursos
                if (comp != null) {
                    comp._unmountRecursive();
                }
            })
            .build();
    }

    /** Devuelve (o crea) la página asociada al path y sesión, e inyecta los @Param */
    public HtmlComponent getPage(String sessionId, String path) {
        PageKey key = new PageKey(sessionId, path);
        
        // ✅ Caffeine gestiona la creación atómica con 'get'
        return instances.get(key, k -> {
            RouteRegistry.Result res = registry.resolveWithInstance(path);
            HtmlComponent comp = res.component();
            comp._injectParams(res.params());
            paramsByPage.put(k, res.params());
            return comp;
        });
    }

    /** Obtén los parámetros de ruta ya parseados para esa sesión+path */
    public Map<String,String> getParams(String sessionId, String path) {
        return paramsByPage.getOrDefault(new PageKey(sessionId, path), Map.of());
    }

    /** Atajo para la home de una sesión concreta */
    public HtmlComponent getHomePageInstance(String sessionId) {
        return getPage(sessionId, "/");
    }

    /** * ✅ FIX: Ya NO borramos la página manualmente al cambiar de ruta.
     * Esto permite que si el usuario tiene la misma página abierta en otra pestaña,
     * esa pestaña siga viva. La página expirará sola por tiempo (Caffeine).
     */
    public void evict(String sessionId, String path) {
        // NO-OP: Dejamos que el Cache expire solo.
        // (El WebSocket handler llama a esto, pero ahora lo ignoramos intencionalmente)
    }

    /**
     * Limpia TODAS las páginas de una sesión (Logout / Expiración HTTP).
     * Esto es vital para liberar memoria cuando el usuario se va definitivamente.
     */
    public void evictAll(String sessionId) {
        // Al eliminar las claves del mapa de Caffeine, se dispara el removalListener
        // que se encarga de llamar a _unmountRecursive() automáticamente.
        instances.asMap().keySet().removeIf(key -> key.sessionId().equals(sessionId));
    }
}