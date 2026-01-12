package com.ciro.jreactive.standalone;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

public final class ObjectMapperFactory {

    private ObjectMapperFactory() {}

    public static ObjectMapper create() {
        return JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .addModule(new Jdk8Module())
            .addModule(new ParameterNamesModule())
            //.visibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
            .build();
    }
}
