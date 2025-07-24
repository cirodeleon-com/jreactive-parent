package com.ciro.jreactive;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;





/** Guarda los @Call:  Bean Validation + rate‑limit por método. */
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

    public void validateParams(Object target, java.lang.reflect.Method m, Object[] args) {
        var viol = validator.forExecutables()
                            .validateParameters(target, m, args);
        if (!viol.isEmpty()) {
            ConstraintViolation<?> v = viol.iterator().next();
            throw new IllegalArgumentException(v.getMessage());
        }
    }

    /* true si todavía está dentro del límite */
    public boolean tryConsume(String key) {
        Bucket b = BUCKETS.computeIfAbsent(key,
            k -> Bucket.builder()                 // ←   builder() está en Bucket
                      .addLimit(Bandwidth.simple(RATE, WINDOW))
                      .build());
        return b.tryConsume(1);
    }

    public String errorJson(String code, String msg) {
        try { return mapper.writeValueAsString(Map.of("error", msg, "code", code)); }
        catch (Exception e) { return "{\"error\":\""+msg+"\",\"code\":\""+code+"\"}"; }
    }
}

