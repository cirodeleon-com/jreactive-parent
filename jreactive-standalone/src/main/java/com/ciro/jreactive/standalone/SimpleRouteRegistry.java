package com.ciro.jreactive.standalone;

import com.ciro.jreactive.HtmlComponent;
import com.ciro.jreactive.router.PathPattern;
import com.ciro.jreactive.router.RouteProvider;

import java.util.*;
import java.util.function.Supplier;

/**
 * Registro de rutas manual para Standalone.
 * Reemplaza la detección automática de Spring.
 */
public class SimpleRouteRegistry implements RouteProvider {

    // Lista simple de rutas registradas
    private final List<Entry> routes = new ArrayList<>();

    // Guardamos el patrón compilado y la "fábrica" (Supplier) para crear el componente
    private record Entry(PathPattern pattern, Supplier<HtmlComponent> factory) {}

    /**
     * Registra una ruta nueva.
     * Ejemplo: registry.add("/users/{id}", UserPage::new);
     */
    public void add(String path, Supplier<HtmlComponent> factory) {
        routes.add(new Entry(PathPattern.compile(path), factory));
    }

    @Override
    public Result resolve(String path) {
        // Buscamos la primera ruta que coincida
        for (Entry e : routes) {
            Map<String, String> params = e.pattern.match(path);
            if (params != null) {
                // ¡Éxito! Devolvemos una NUEVA instancia del componente y los parámetros extraídos
                return new Result(e.factory.get(), params);
            }
        }
        // Si llegamos aquí, es un 404
        throw new RuntimeException("No route found for path: " + path);
    }
}