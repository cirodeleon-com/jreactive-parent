package com.ciro.jreactive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

 @Bean              // ← este mapper será el “oficial” en toda la app
 public ObjectMapper objectMapper() {
     return JsonMapper.builder()
             .addModule(new JavaTimeModule())      // LocalDate, Instant, etc.
             .addModule(new Jdk8Module())          // Optional<>, etc.
             .addModule(new ParameterNamesModule())// records / ctor-based
             .build();
 }
}

