package com.ciro.jreactive.spi;

import com.ciro.jreactive.HtmlComponent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AccessorRegistry {
    
    // Cache de accessors por clase
    private static final Map<Class<?>, ComponentAccessor<?>> cache = new ConcurrentHashMap<>();

    // Registra un accessor (llamado por el código generado)
    public static void register(Class<?> clazz, ComponentAccessor<?> accessor) {
        cache.put(clazz, accessor);
    }

    // Obtiene un accessor (usado por el Runtime)
    @SuppressWarnings("unchecked")
    public static <T extends HtmlComponent> ComponentAccessor<T> get(Class<T> clazz) {
        ComponentAccessor<?> acc = cache.get(clazz);
        // Si no existe, podríamos intentar cargarlo por nombre (Lazy Loading) o devolver null
        // Para esta fase, si retorna null, el runtime usará fallback a reflexión.
        return (ComponentAccessor<T>) acc;
    }
    
    public static boolean has(Class<?> clazz) {
        return cache.containsKey(clazz);
    }
}