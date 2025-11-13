package com.ciro.jreactive;

import com.ciro.jreactive.router.RouteRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PageResolver {

    private final RouteRegistry registry;

    /** Clave compuesta: sesión + path concreto ("/users/42") */
    private record PageKey(String sessionId, String path) {}

    /** Instancias cacheadas por (sessionId, path) */
    private final Map<PageKey, HtmlComponent> instances = new ConcurrentHashMap<>();

    /** Parámetros extraídos del path, también por (sessionId, path) */
    private final Map<PageKey, Map<String,String>> paramsByPage = new ConcurrentHashMap<>();

    public PageResolver(RouteRegistry registry) {
        this.registry = registry;
    }

    /** Devuelve (o crea) la página asociada al path y sesión, e inyecta los @Param */
    public HtmlComponent getPage(String sessionId, String path) {
        PageKey key = new PageKey(sessionId, path);
        return instances.computeIfAbsent(key, p -> {
            RouteRegistry.Result res = registry.resolveWithInstance(path);
            HtmlComponent comp = res.component();
            comp._injectParams(res.params());          // método package-private en HtmlComponent
            paramsByPage.put(p, res.params());
            return comp;
        });
    }

    /** Obtén los parámetros de ruta ya parseados para esa sesión+path (útil para @Call, etc.) */
    public Map<String,String> getParams(String sessionId, String path) {
        return paramsByPage.getOrDefault(new PageKey(sessionId, path), Map.of());
    }

    /** Atajo para la home de una sesión concreta */
    public HtmlComponent getHomePageInstance(String sessionId) {
        return getPage(sessionId, "/");
    }

    /** Limpia cache SOLO para la sesión + path concretos (ej. route-change) */
    public void evict(String sessionId, String path) {
        PageKey key = new PageKey(sessionId, path);
        HtmlComponent inst = instances.remove(key);
        paramsByPage.remove(key);
        if (inst != null) {
            inst._unmountRecursive();              // ← UNMOUNT aquí (en cascada)
        }
    }
}
