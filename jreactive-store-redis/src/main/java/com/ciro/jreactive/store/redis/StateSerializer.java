package com.ciro.jreactive.store.redis;

public interface StateSerializer {
    byte[] serialize(Object obj);
    
    // Le pasamos el tipo esperado (generalmente HtmlComponent.class)
    <T> T deserialize(byte[] bytes, Class<T> type);
    
    String name(); // Para logs ("json" o "fst")
}