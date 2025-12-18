package com.ciro.jreactive;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import com.fasterxml.jackson.databind.ObjectMapper;

// ✅ Imports de Caffeine (La solución al Memory Leak)
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

/**
 * Guarda los @Call: Bean Validation + rate-limit por método.
 * <p>
 * FIX: Se reemplazó el ConcurrentHashMap estático por Caffeine Cache.
 * Esto previene Memory Leaks limpiando buckets inactivos tras 1 hora.
 */
@Component
public final class CallGuard {

    private static final int RATE = 60;                  // 60 peticiones / seg
    private static final Duration WINDOW = Duration.ofSeconds(1);

    // ✅ FIX: Cache inteligente en lugar de Mapa estático infinito.
    // - expireAfterAccess: Si el usuario no llama al método en 1h, liberamos RAM.
    // - maximumSize: Protege contra ataques que intenten saturar la memoria.
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(1))
            .maximumSize(50_000)
            .build();

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

    /* true si todavía está dentro del límite */
    public boolean tryConsume(String key) {
        // ✅ USO DE CAFFEINE:
        // Obtiene el bucket existente o crea uno nuevo de forma atómica y segura.
        Bucket b = buckets.get(key, k -> 
             Bucket.builder()
                   .addLimit(Bandwidth.builder()
            		    .capacity(RATE)
            		    .refillGreedy(RATE, WINDOW)
            		    .build())
                   .build()
        );
        
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