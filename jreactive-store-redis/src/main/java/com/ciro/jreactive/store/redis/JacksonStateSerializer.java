package com.ciro.jreactive.store.redis;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

public class JacksonStateSerializer implements StateSerializer {

    private final ObjectMapper mapper;

    public JacksonStateSerializer(ObjectMapper originalMapper) {
        // Hacemos una copia para no ensuciar el mapper global de Spring
        this.mapper = originalMapper.copy();
        
        // 1. Ignorar propiedades desconocidas (La clave de la robustez)
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // 2. Activar Polimorfismo (Para saber que este JSON es una UserPage.class)
        // Esto agrega un campo "@class": "com.ciro.app.UserPage" al JSON
        var ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build();
        
        this.mapper.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
    }

    @Override
    public byte[] serialize(Object obj) {
        try {
            return mapper.writeValueAsBytes(obj);
        } catch (Exception e) {
            throw new RuntimeException("Error serializando estado a JSON", e);
        }
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> type) {
        try {
            return mapper.readValue(bytes, type);
        } catch (Exception e) {
            throw new RuntimeException("Error deserializando estado desde JSON", e);
        }
    }

    @Override
    public String name() {
        return "json";
    }
}