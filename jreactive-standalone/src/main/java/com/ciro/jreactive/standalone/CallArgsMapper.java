package com.ciro.jreactive.standalone;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class CallArgsMapper {
    private CallArgsMapper() {}

    @SuppressWarnings("unchecked")
    public static Object[] mapArgs(ObjectMapper om, Validator validator, java.lang.reflect.Method target, Object argsNode) throws Exception {
        Parameter[] params = target.getParameters();
        if (params.length == 0) return new Object[0];

        List<Object> argsList = new ArrayList<>();
        List<Object> rawArgs = (argsNode instanceof List) ? (List<Object>) argsNode : List.of();

        for (int i = 0; i < params.length; i++) {
            Parameter p = params[i];
            Object converted = null;

            if (i < rawArgs.size()) {
                Object raw = rawArgs.get(i);
                // Convertir raw -> tipo del parámetro usando Jackson
                converted = om.convertValue(raw, om.constructType(p.getParameterizedType()));
            } else {
                converted = null;
            }

            // Validación Jakarta si aplica
            if (validator != null && converted != null) {
                Set<ConstraintViolation<Object>> violations = (Set) validator.validate(converted);
                if (!violations.isEmpty()) {
                    throw new ConstraintViolationException(violations);
                }
            }

            argsList.add(converted);
        }

        return argsList.toArray();
    }
}
