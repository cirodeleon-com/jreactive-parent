package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.router.Param;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JrxHttpApi {

    private final PageResolver pageResolver;
    private final ObjectMapper objectMapper;
    private final CallGuard guard;

    public JrxHttpApi(PageResolver pageResolver, ObjectMapper objectMapper, CallGuard guard) {
        this.pageResolver = pageResolver;
        this.objectMapper = objectMapper;
        this.guard = guard;
    }

    /** Render HTML del componente asociado a sessionId + path */
    public String render(String sessionId, String path) {
        HtmlComponent page = pageResolver.getPage(sessionId, path);
        return page.render();
    }

    /** Ejecuta un @Call (qualified = "CompId.metodo" o "metodo" en raíz) */
    public String call(String sessionId, String path, String qualified, Map<String, Object> body) {

        HtmlComponent page = pageResolver.getPage(sessionId, path);

        // 1) localizar método
        var callables = collectCallables(page);
        var entry = callables.get(qualified);
        if (entry == null) {
            return guard.errorJson("NOT_FOUND", "Método no permitido: " + qualified);
        }

        Method target = entry.getKey();
        Object owner = entry.getValue();

        // 2) deserializar args (mezcla body + @Param del path)
        @SuppressWarnings("unchecked")
        List<Object> rawArgs = (List<Object>) body.getOrDefault("args", List.of());
        Parameter[] params = target.getParameters();
        Object[] args = new Object[params.length];

        Map<String, String> routeParams = pageResolver.getParams(sessionId, path);
        if (routeParams == null) routeParams = Map.of();

        for (int i = 0; i < params.length; i++) {
            Parameter p = params[i];
            Object raw = i < rawArgs.size() ? rawArgs.get(i) : null;

            Param ann = p.getAnnotation(Param.class);
            if (ann != null) {
                raw = routeParams.get(ann.value());
            }

            JavaType type = objectMapper.getTypeFactory().constructType(p.getParameterizedType());
            args[i] = objectMapper.convertValue(raw, type);
        }

        // 3) rate limit (por sesión + método)
        String rateKey = sessionId + ":" + qualified;
        if (!guard.tryConsume(rateKey)) {
            return guard.errorJson("RATE_LIMIT", "Demasiadas llamadas, inténtalo en un instante");
        }

        // 4) Bean Validation
        var violations = guard.validateParams(owner, target, args);
        if (!violations.isEmpty()) {
            return guard.validationJson(violations);
        }

        // 5) invocar
        try {
            if (owner instanceof HtmlComponent comp) {
                comp._captureStateSnapshot();
            }

            Object result = target.invoke(owner, args);

            Call callAnn = target.getAnnotation(Call.class);
            if (callAnn != null && callAnn.sync() && owner instanceof HtmlComponent comp) {
                comp._syncState();
            }

            Map<String, Object> envelope = new HashMap<>();
            envelope.put("ok", true);
            if (result != null) envelope.put("result", result);

            return objectMapper.writeValueAsString(envelope);

        } catch (Exception e) {
            e.printStackTrace();
            return guard.errorJson("INVOKE_ERROR",
                    "Error al invocar " + qualified + ": " + e.getMessage());
        }
    }

    /** Igualito a tu lógica actual, movida al core */
    private Map<String, Map.Entry<Method, HtmlComponent>> collectCallables(HtmlComponent rootPage) {
        Map<String, Map.Entry<Method, HtmlComponent>> map = new HashMap<>();
        collectCallables(rootPage, rootPage, map);
        return map;
    }

    private void collectCallables(
            HtmlComponent rootPage,
            HtmlComponent current,
            Map<String, Map.Entry<Method, HtmlComponent>> map
    ) {
        String compId = current.getId();

        for (var e : current.getCallableMethods().entrySet()) {
            String methodName = e.getKey();
            Method m = e.getValue();

            // clave completa siempre
            map.put(compId + "." + methodName, Map.entry(m, current));

            // clave corta solo para la raíz
            if (current == rootPage) {
                map.put(methodName, Map.entry(m, current));
            }
        }

        for (HtmlComponent child : current._children()) {
            collectCallables(rootPage, child, map);
        }
    }
}

