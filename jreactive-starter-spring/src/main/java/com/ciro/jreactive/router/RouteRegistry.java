package com.ciro.jreactive.router;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import com.ciro.jreactive.HtmlComponent;

@Component                       // se detecta al arrancar Spring
public class RouteRegistry {
    private final Map<String, Supplier<HtmlComponent>> routes = new HashMap<>();

    public RouteRegistry(ApplicationContext ctx) {
        // escanea todos los beans HtmlComponent
        ctx.getBeansOfType(HtmlComponent.class).values().forEach(comp -> {
            Route ann = comp.getClass().getAnnotation(Route.class);
            if (ann != null) routes.put(ann.path(), () -> {
                try { return comp.getClass().getDeclaredConstructor().newInstance(); }
                catch (Exception e) { throw new RuntimeException(e); }
            });
        });
    }
    public HtmlComponent resolve(String path) {
        return Optional.ofNullable(routes.get(path))
                       .orElse(routes.get("/"))      // fallback a Home
                       .get();
    }
    
    public HtmlComponent resolveWithInstance(String path) {
        Supplier<HtmlComponent> factory = Optional.ofNullable(routes.get(path))
                                                   .orElse(routes.get("/"));
        return factory.get();
    }

}
