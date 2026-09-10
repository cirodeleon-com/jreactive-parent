package com.ciro.jreactive;

import com.ciro.jreactive.annotations.OnEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class JrxEventDispatcher {

    private static final Map<Class<?>, Map<String, List<Method>>> CACHE =
            new ConcurrentHashMap<>();

    private JrxEventDispatcher() {
    }

    static boolean dispatch(
            HtmlComponent page,
            String event
    ) {
        if (!serverPage(page)) {
            return false;
        }

        if (!eventName(event)) {
            return false;
        }

        List<Method> handlers =
                handlers(page.getClass(), event);

        if (handlers.isEmpty()) {
            return false;
        }

        page._captureStateSnapshot();

        for (Method handler : handlers) {
            invoke(page, handler);
        }

        page._syncState();
        return true;
    }

    private static boolean serverPage(
            HtmlComponent page
    ) {
        return page != null
                && !page.isStateless();
    }

    private static boolean eventName(
            String event
    ) {
        return event != null
                && !event.isBlank();
    }

    private static List<Method> handlers(
            Class<?> type,
            String event
    ) {
        return CACHE
                .computeIfAbsent(
                        type,
                        JrxEventDispatcher::scan
                )
                .getOrDefault(
                        event,
                        List.of()
                );
    }

    private static Map<String, List<Method>> scan(
            Class<?> type
    ) {
        Map<String, List<Method>> result =
                new HashMap<>();

        for (Method method : type.getMethods()) {
            OnEvent annotation =
                    method.getAnnotation(OnEvent.class);

            if (annotation == null) {
                continue;
            }

            validateHandler(method, annotation);

            result.computeIfAbsent(
                    annotation.value().trim(),
                    ignored -> new ArrayList<>()
            ).add(method);
        }

        result.replaceAll(
                (event, methods) ->
                        List.copyOf(methods)
        );

        return Map.copyOf(result);
    }

    private static void validateHandler(
            Method method,
            OnEvent annotation
    ) {
        validateName(method, annotation);
        validateParameters(method);
        validateReturn(method);
        validateStatic(method);
    }

    private static void validateName(
            Method method,
            OnEvent annotation
    ) {
        if (annotation.value().isBlank()) {
            throw invalid(
                    method,
                    "el nombre del evento está vacío"
            );
        }
    }

    private static void validateParameters(
            Method method
    ) {
        if (method.getParameterCount() != 0) {
            throw invalid(
                    method,
                    "V1 no admite parámetros"
            );
        }
    }

    private static void validateReturn(
            Method method
    ) {
        if (method.getReturnType() != void.class) {
            throw invalid(
                    method,
                    "V1 requiere retorno void"
            );
        }
    }

    private static void validateStatic(
            Method method
    ) {
        if (Modifier.isStatic(method.getModifiers())) {
            throw invalid(
                    method,
                    "el método no puede ser static"
            );
        }
    }

    private static IllegalStateException invalid(
            Method method,
            String reason
    ) {
        return new IllegalStateException(
                "JReactive @OnEvent inválido en "
                        + method.getDeclaringClass().getName()
                        + "."
                        + method.getName()
                        + ": "
                        + reason
        );
    }

    private static void invoke(
            HtmlComponent page,
            Method method
    ) {
        try {
            method.invoke(page);
        } catch (InvocationTargetException e) {
            rethrow(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "No se pudo ejecutar @OnEvent "
                            + method.getName(),
                    e
            );
        }
    }

    private static void rethrow(
            Throwable cause
    ) {
        if (cause instanceof RuntimeException runtime) {
            throw runtime;
        }

        if (cause instanceof Error error) {
            throw error;
        }

        throw new IllegalStateException(
                "El handler @OnEvent falló",
                cause
        );
    }
}