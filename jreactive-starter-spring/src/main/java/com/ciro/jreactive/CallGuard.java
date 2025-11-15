package com.ciro.jreactive;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import org.springframework.stereotype.Component;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;





/** Guarda los @Call:  Bean Validation + rate‑limit por método. */
@Component
public final class CallGuard {

    private static final int RATE = 60;                   // 10 peticiones / seg
    private static final Duration WINDOW = Duration.ofSeconds(1);
    private static final Map<String, Bucket> BUCKETS = new ConcurrentHashMap<>();

    private final Validator validator;
    private final ObjectMapper mapper;

    public CallGuard(Validator validator, ObjectMapper mapper) {
        this.validator = validator;
        this.mapper    = mapper;
    }

    public Set<ConstraintViolation<Object>> validateParams(
            Object target,
            java.lang.reflect.Method m,
            Object[] args
    ) {
        @SuppressWarnings("unchecked")
        Set<ConstraintViolation<Object>> viol =
                (Set<ConstraintViolation<Object>>) (Set<?>)
                        validator.forExecutables().validateParameters(target, m, args);

        return viol; // JReactiveApplication decidirá qué hacer con ellas
    }


    /* true si todavía está dentro del límite */
    public boolean tryConsume(String key) {
        Bucket b = BUCKETS.computeIfAbsent(key,
            k -> Bucket.builder()                 // ←   builder() está en Bucket
                      .addLimit(Bandwidth.simple(RATE, WINDOW))
                      .build());
        return b.tryConsume(1);
    }
    
    /** De "register.form.name" → "form.name" (para el front) */
    private static String extractFieldPath(String propertyPath) {
        if (propertyPath == null) return "";
        int dot = propertyPath.indexOf('.');
        return (dot < 0) ? propertyPath : propertyPath.substring(dot + 1);
    }


    public String errorJson(String code, String msg) {
        try {
            return mapper.writeValueAsString(
                Map.of(
                    "ok",   false,
                    "error", msg,
                    "code",  code
                )
            );
        } catch (Exception e) {
            return "{\"ok\":false,\"error\":\"" + msg + "\",\"code\":\"" + code + "\"}";
        }
    }
    
    public String validationJson(Set<ConstraintViolation<Object>> violations) {
        // Mensaje global (el primero)
        String global = violations.stream()
            .findFirst()
            .map(ConstraintViolation::getMessage)
            .orElse("Datos inválidos");

        // Lista detallada para el front
        var list = violations.stream()
            .map(v -> Map.of(
                "param",      v.getPropertyPath().toString(), // "register.form.name"
                "path",       extractFieldPath(v.getPropertyPath().toString()), // "form.name"
                "message",    v.getMessage(),
                "constraint", v.getConstraintDescriptor()
                               .getAnnotation()
                               .annotationType()
                               .getSimpleName()
            ))
            .toList();

        try {
            return mapper.writeValueAsString(
                Map.of(
                    "ok",         false,
                    "code",       "VALIDATION",
                    "error",      global,
                    "violations", list
                )
            );
        } catch (Exception e) {
            // Si algo falla serializando, al menos devolvemos un error simple
            return errorJson("VALIDATION", global);
        }
    }


}

