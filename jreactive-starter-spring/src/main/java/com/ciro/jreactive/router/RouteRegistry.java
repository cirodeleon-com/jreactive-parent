package com.ciro.jreactive.router;

import com.ciro.jreactive.HtmlComponent;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Supplier;

/**
 * Registra rutas anotadas con {@link Route} y permite resolver
 * paths dinámicos como "/users/{id}" devolviendo:
 *  - una NUEVA instancia del componente (creada por Spring)
 *  - los valores de los parámetros extraídos del path
 */
@Component
public class RouteRegistry {

    /** Entrada interna: template original, regex compilado y factory */
    private static final class Entry {
        final String template;
        final PathPattern pattern;
        final Supplier<HtmlComponent> factory;

        Entry(String template, Supplier<HtmlComponent> factory) {
            this.template = template;
            this.pattern  = PathPattern.compile(template);
            this.factory  = factory;
        }
    }

    /** Resultado público: componente + params del path */
    public static record Result(HtmlComponent component, Map<String, String> params) {}

    private final List<Entry> routes = new ArrayList<>();

    public RouteRegistry(ApplicationContext ctx) {
        AutowireCapableBeanFactory beanFactory = ctx.getAutowireCapableBeanFactory();

        // Escanea todos los HtmlComponent registrados como beans
        ctx.getBeansOfType(HtmlComponent.class).values().forEach(bean -> {
            Route ann = bean.getClass().getAnnotation(Route.class);
            if (ann == null) return;

            Class<? extends HtmlComponent> clazz = bean.getClass();

            // ✅ Cada instancia se crea a través de Spring (DI, @Autowired, @Value, etc.)
            Supplier<HtmlComponent> sup = () -> beanFactory.createBean(clazz);

            routes.add(new Entry(ann.path(), sup));
        });

        // Asegura que exista "/"
        boolean hasRoot = routes.stream().anyMatch(e -> "/".equals(e.template));
        if (!hasRoot) {
            throw new IllegalStateException("Debe existir al menos una ruta @Route(path=\"/\")");
        }
    }

    /**
     * Versión antigua: sólo devuelve el componente.
     * Si el path tiene parámetros, éstos se ignoran.
     * (Se mantiene por compatibilidad si lo usas en otro lado)
     */
    public HtmlComponent resolve(String path) {
        return resolveWithInstance(path).component();
    }

    /**
     * Devuelve SIEMPRE una nueva instancia + mapa de parámetros extraídos.
     * Si no hace match ninguna ruta, cae en "/".
     */
    public Result resolveWithInstance(String path) {
        for (Entry e : routes) {
            Map<String, String> params = e.pattern.match(path);
            if (params != null) {
                return new Result(e.factory.get(), params);
            }
        }
        // Fallback al root
        Entry root = routes.stream()
                           .filter(r -> "/".equals(r.template))
                           .findFirst()
                           .orElseThrow(); // imposible si validamos en ctor
        return new Result(root.factory.get(), Map.of());
    }
}
