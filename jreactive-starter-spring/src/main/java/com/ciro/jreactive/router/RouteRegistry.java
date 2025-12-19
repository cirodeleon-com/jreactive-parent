package com.ciro.jreactive.router;

import com.ciro.jreactive.HtmlComponent;
// 🔥 Importamos la interfaz que definiste en el Core
import com.ciro.jreactive.router.RouteProvider; 
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Supplier;

@Component
public class RouteRegistry implements RouteProvider {

    // --- MISMAS CLASES INTERNAS (Entry) ---
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

    // ❌ ELIMINADO: 'public static record Result' 
    // (Porque ahora usamos RouteProvider.Result que viene del Core)

    private final List<Entry> routes = new ArrayList<>();

    // --- MISMO CONSTRUCTOR (Lógica de escaneo idéntica) ---
    public RouteRegistry(ApplicationContext ctx) {
        AutowireCapableBeanFactory beanFactory = ctx.getAutowireCapableBeanFactory();

        // 1. Escanea Beans
        ctx.getBeansOfType(HtmlComponent.class).values().forEach(bean -> {
            Route ann = bean.getClass().getAnnotation(Route.class);
            if (ann == null) return;

            Class<? extends HtmlComponent> clazz = bean.getClass();

            // 2. Crea Factory de Spring (Vital para prototype scope)
            Supplier<HtmlComponent> sup = () -> beanFactory.createBean(clazz);

            routes.add(new Entry(ann.path(), sup));
        });

        // 3. Validación de root
        boolean hasRoot = routes.stream().anyMatch(e -> "/".equals(e.template));
        if (!hasRoot) {
            throw new IllegalStateException("Debe existir al menos una ruta @Route(path=\"/\")");
        }
    }

    // --- MÉTODO ADAPTADO (Misma lógica, nuevo nombre de retorno) ---
    
    @Override
    public RouteProvider.Result resolve(String path) {
        // 1. Intentar hacer match
        for (Entry e : routes) {
            Map<String, String> params = e.pattern.match(path);
            if (params != null) {
                // ✅ ÉXITO: Devolvemos el Result del Core
                return new RouteProvider.Result(e.factory.get(), params);
            }
        }
        
        // 2. Fallback al root "/" (Igual que antes)
        Entry root = routes.stream()
                           .filter(r -> "/".equals(r.template))
                           .findFirst()
                           .orElseThrow(); // Imposible fallar si pasó el constructor
                           
        return new RouteProvider.Result(root.factory.get(), Map.of());
    }
}