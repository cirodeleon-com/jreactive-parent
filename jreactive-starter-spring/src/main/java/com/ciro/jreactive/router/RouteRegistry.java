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

        // 1. Autodescubrir el paquete principal del usuario (donde está su @SpringBootApplication)
        String basePackage = "com.ciro"; // Fallback por defecto
        var bootApps = ctx.getBeansWithAnnotation(org.springframework.boot.autoconfigure.SpringBootApplication.class);
        if (!bootApps.isEmpty()) {
            basePackage = bootApps.values().iterator().next().getClass().getPackageName();
        }

        // 2. Escanear el classpath buscando tu anotación @Route directamente (No necesita @Component)
        org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider scanner = 
                new org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new org.springframework.core.type.filter.AnnotationTypeFilter(Route.class));

        for (org.springframework.beans.factory.config.BeanDefinition bd : scanner.findCandidateComponents(basePackage)) {
            try {
                Class<?> clazz = Class.forName(bd.getBeanClassName());
                Route ann = clazz.getAnnotation(Route.class);
                
                if (ann != null && HtmlComponent.class.isAssignableFrom(clazz)) {
                    @SuppressWarnings("unchecked")
                    Class<? extends HtmlComponent> compClass = (Class<? extends HtmlComponent>) clazz;
                    
                    // La magia: createBean() inyectará los @Autowired automáticamente 
                    // aunque la clase no esté registrada como @Component
                    Supplier<HtmlComponent> sup = () -> beanFactory.createBean(compClass);
                    routes.add(new Entry(ann.path(), sup));
                }
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

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