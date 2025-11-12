package com.ciro.jreactive;

import com.ciro.jreactive.router.RouteRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PageResolver {

    private final RouteRegistry registry;

    /** Instancias cacheadas por path concreto ("/users/42") */
    private final Map<String, HtmlComponent> instances = new ConcurrentHashMap<>();

    /** Parámetros extraídos del path, también por path concreto */
    private final Map<String, Map<String,String>> paramsByPath = new ConcurrentHashMap<>();

    public PageResolver(RouteRegistry registry) {
        this.registry = registry;
    }

    /** Devuelve (o crea) la página asociada al path y le inyecta los @Param */
    public HtmlComponent getPage(String path) {
        return instances.computeIfAbsent(path, p -> {
            RouteRegistry.Result res = registry.resolveWithInstance(p);
            HtmlComponent comp = res.component();
            comp._injectParams(res.params());          // método package-private en HtmlComponent
            paramsByPath.put(p, res.params());
            return comp;
        });
    }

    /** Obtén los parámetros de ruta ya parseados (útil para @Call, etc.) */
    public Map<String,String> getParams(String path) {
        return paramsByPath.getOrDefault(path, Map.of());
    }

    /** Atajo para la home */
    public HtmlComponent getHomePageInstance() {
        return getPage("/");
    }

    /** Opcional: limpiar cache si quieres forzar re-instalación de páginas dinámicas */
    public void evict(String path) {
        instances.remove(path);
        paramsByPath.remove(path);
    }
}
