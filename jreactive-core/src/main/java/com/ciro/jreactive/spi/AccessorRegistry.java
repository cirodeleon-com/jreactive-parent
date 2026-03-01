package com.ciro.jreactive.spi;

import com.ciro.jreactive.HtmlComponent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AccessorRegistry {
    
    // Cache de accessors por clase
    private static final Map<Class<?>, ComponentAccessor<?>> cache = new ConcurrentHashMap<>();

    // Objeto centinela para indicar que YA buscamos y NO existe (evita reintentos fallidos)
    private static final ComponentAccessor<?> NO_OP = new ComponentAccessor<>() {
        @Override public void write(HtmlComponent c, String p, Object v) {}
        @Override public Object read(HtmlComponent c, String p) { return null; }
        @Override public Object call(HtmlComponent c, String m, Object... a) { return null; }
    };

    // Registra un accessor (llamado por el código generado en su bloque static)
    public static void register(Class<?> clazz, ComponentAccessor<?> accessor) {
        cache.put(clazz, accessor);
    }

    // Obtiene un accessor (usado por el Runtime)
    @SuppressWarnings("unchecked")
    public static <T extends HtmlComponent> ComponentAccessor<T> get(Class<T> clazz) {
        // 1. Busqueda rápida en caché
        ComponentAccessor<?> acc = cache.get(clazz);
        
        // Si ya sabemos que no existe (NO_OP), devolvemos null rápido
        if (acc == NO_OP) return null; 
        
        if (acc != null) {
            return (ComponentAccessor<T>) acc;
        }

        // 2. 🔥 LAZINESS: Intentar cargar la clase generada dinámicamente
        // Si tienes "UserPage", buscamos "UserPage__Accessor"
        try {
            String accessorClassName = clazz.getName() + "__Accessor";
            
            // Al cargar la clase, se ejecuta su bloque static {}, que llama a register()
            Class.forName(accessorClassName); 
            
            // Ahora debería estar en caché
            acc = cache.get(clazz);
            
            if (acc != null) {
                System.out.println("⚡ [AOT] Accessor cargado y activado: " + accessorClassName);
                return (ComponentAccessor<T>) acc;
            }
        } catch (ClassNotFoundException e) {
            // El componente no tiene Accessor generado (quizás no pasó por el APT)
            // Guardamos NO_OP para no intentar cargarlo mil veces y matar el rendimiento
            cache.put(clazz, NO_OP);
        } catch (Exception e) {
            e.printStackTrace(); // Error raro (ej: fallo en static block)
        }

        // Si llegamos aquí, usamos el modo lento (Reflection)
        return null;
    }
    
    // 🔥 CORRECCIÓN AQUÍ: Agregamos @SuppressWarnings y el cast (Class<HtmlComponent>)
    @SuppressWarnings("unchecked")
    public static boolean has(Class<?> clazz) {
        // Forzamos el tipo. Si 'clazz' no es un HtmlComponent, get() simplemente
        // no encontrará el accessor (o lanzará error interno manejado), pero compilará.
        return get((Class<HtmlComponent>) clazz) != null;
    }
}