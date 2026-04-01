package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.router.Layout;
import com.ciro.jreactive.router.UrlVariable;
import com.ciro.jreactive.spi.AccessorRegistry;
import com.ciro.jreactive.spi.ComponentAccessor;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class JrxHttpApi {

    private final PageResolver pageResolver;
    private final ObjectMapper objectMapper;
    private final CallGuard guard;
    private final boolean persistenceEnabled;
    private final JrxHubManager hubManager;

    public JrxHttpApi(PageResolver pageResolver, ObjectMapper objectMapper, CallGuard guard, boolean persistenceEnabled, JrxHubManager hubManager) {
        this.pageResolver = pageResolver;
        this.objectMapper = objectMapper;
        this.guard = guard;
        this.persistenceEnabled = persistenceEnabled;
        this.hubManager = hubManager;
    }

    /** Render HTML del componente asociado a sessionId + path */
    public String render(String sessionId, String path, boolean renderLayout, Map<String, String> queryParams) {
        HtmlComponent page = pageResolver.getPage(sessionId, path, queryParams);
        
        // 🔥 Lo tuyo intacto: Extraemos el diccionario de @UrlParam del componente actual
        String urlParamsJson = "{}";
        try {
            urlParamsJson = objectMapper.writeValueAsString(page._getUrlBindings());
        } catch (Exception e) {
        	System.err.println("⚠️ [JReactive] Error serializando los @UrlParam de la página: " + e.getMessage());
        }
        String script = "<script>window.__JRX_URL_PARAMS__ = " + urlParamsJson + ";</script>";

        // 🟢 MODIFICACIÓN: Declaramos variables para guardar el resultado en lugar de retornar de inmediato
        String finalHtml;
        HtmlComponent layoutInstance = null;

        // 1. Si es petición parcial (AJAX/SPA), devolvemos el script + la página
        if (!renderLayout) {
            finalHtml = script + "\n" + page.render(); // 👈 Cambio: Asignar en vez de retornar
        } else {
            // 2. Si es carga completa, buscamos si tiene @Layout
            Layout layoutAnn = page.getClass().getAnnotation(Layout.class);
            
            if (layoutAnn != null) {
                try {
                    // Creamos una instancia fresca del Layout
                    layoutInstance = layoutAnn.value().getDeclaredConstructor().newInstance(); // 👈 Cambio: Guardar referencia
                    
                    // Inyección: Renderizamos la página y se la pasamos al layout en el slot por defecto
                    layoutInstance._setSlots(java.util.Map.of("default", page.render()));
                    
                    // Renderizamos el layout
                    String fullHtml = layoutInstance.render();
                    
                    // 🔥 Inyección quirúrgica: Lo metemos DENTRO del <head> para no romper el <!DOCTYPE>
                    if (fullHtml.contains("</head>")) {
                        finalHtml = fullHtml.replaceFirst("</head>", script + "\n</head>"); // 👈 Cambio: Asignar
                    } else {
                        finalHtml = script + "\n" + fullHtml; // 👈 Cambio: Asignar
                    }
                    
                } catch (Exception e) {
                    e.printStackTrace();
                    // Si falla el layout, devolvemos la página "cruda" como fallback
                    finalHtml = script + "\n" + page.render(); // 👈 Cambio: Asignar
                }
            } else {
                // 3. Si no tiene layout, se devuelve cruda con el script arriba
                finalHtml = script + "\n" + page.render(); // 👈 Cambio: Asignar
            }
        }

        // 🟢 LO NUEVO (EL DEBER SER): Al final, la ruta decide si inyecta el token
        if (page.isStateless()) {
            Map<String, ReactiveVar<?>> allBindings = new java.util.HashMap<>();
            
            // Extraemos la memoria de la página...
            collectBindingsRecursive(page, allBindings);
            // ... y del layout (si el usuario usó uno)
            if (layoutInstance != null) {
                collectBindingsRecursive(layoutInstance, allBindings);
            }
            
            // Inyectamos el Token con todo el estado consolidado
            finalHtml = injectStatelessToken(finalHtml, allBindings, page.getId());
        }

        return finalHtml; // 👈 Retornamos el producto final ensamblado
    }

    /** Ejecuta un @Call (qualified = "CompId.metodo" o "metodo" en raíz) */
    public String call(String sessionId, String path, String qualified, Map<String, Object> body, Map<String, String> queryParams) {

        HtmlComponent page = pageResolver.getPage(sessionId, path, queryParams);
        
        if (page._state() == ComponentState.UNMOUNTED) {
            System.out.println("♻️ [JrxHttpApi] Página no montada detectada. Hidratando árbol para: " + qualified);
            page.render();
        }
        
        page.render();

        // 1) localizar método
        var callables = collectCallables(page);
        var entry = callables.get(qualified);
        
        if (entry == null && qualified.contains(".")) {
            int dotIdx = qualified.indexOf('.');
            String potentialRef = qualified.substring(0, dotIdx); // "miModal"
            String methodName = qualified.substring(dotIdx + 1);  // "open"
            
            // Le preguntamos a la página raíz si conoce ese alias
            String realId = page._resolveRef(potentialRef); 
            
            if (realId != null) {
                // Traducimos: "miModal.open" -> "ModalTestPage-JModal-0.open"
                String translated = realId + "." + methodName;
                entry = callables.get(translated);
                
                // (Opcional) Debug para ver la magia
                 System.out.println("🔄 Traducción de Call: " + qualified + " -> " + translated);
            }
        }
        
        if (entry == null) {
            System.out.println("⚠️ [JrxHttpApi] Método '" + qualified + "' no encontrado. Reconstruyendo árbol de componentes...");
            
            // Forzamos un renderizado silencioso para que se ejecute el template() 
            // y se instancien/agreguen los hijos (CounterLeaf, etc.) a la lista _children via _addChild()
            page.render(); 

            // Re-escaneamos el árbol ahora que está poblado
            callables = collectCallables(page);
            entry = callables.get(qualified);
        }
        
        if (entry == null) {
            return guard.errorJson("NOT_FOUND", "Método no permitido: " + qualified);
        }
        
        if (hubManager != null) {
            hubManager.ensureSync(sessionId, path, page);
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

            UrlVariable ann = p.getAnnotation(UrlVariable.class);
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

        // 5) EJECUCIÓN CRONOLÓGICA PERFECTA
        try {
            boolean isStateless = page.isStateless();
            Map<String, Object> oldState = new HashMap<>();

            Map<String, ReactiveVar<?>> allBinds = new HashMap<>();
            collectBindingsRecursive(page, allBinds);

            // A. Hidratar desde la Mochila
         // A. Hidratar desde la Mochila
            if (isStateless) {
                String token = (String) body.get("stateToken");
                if (token != null && !token.isBlank()) {
                    oldState = JrxStateToken.decode(token);
                    
                    // 🔥 FIX 1: Ordenar por longitud de clave. Los padres (tipos fuertes como "users") 
                    // se procesan ANTES que los hijos (tipos débiles como "page_table_test-JTable-0.data").
                    List<Map.Entry<String, Object>> sortedEntries = new ArrayList<>(oldState.entrySet());
                    sortedEntries.sort(Comparator.comparingInt(e -> e.getKey().length()));
                    
                    // 🔥 FIX 2: Escudo de Punteros compartidos. Si Padre e Hijo apuntan a la misma memoria, solo lo hidratamos 1 vez.
                    Set<Object> restoredInstances = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

                    for (Map.Entry<String, Object> entryVar : sortedEntries) {
                        @SuppressWarnings("unchecked")
                        ReactiveVar<Object> rv = (ReactiveVar<Object>) allBinds.get(entryVar.getKey());
                        if (rv != null) {
                            Object currentVal = rv.get();
                            
                            // 🔥 FIX QUIRÚRGICO 1: Detectar si es primitivo o String
                            boolean isPrimitive = currentVal != null && (isPrimitiveOrWrapper(currentVal.getClass()) || currentVal instanceof String);

                            // 🔥 FIX QUIRÚRGICO 2: Ignorar primitivos en el chequeo anti-bucles
                            if (!isPrimitive && currentVal != null && restoredInstances.contains(currentVal)) {
                                continue;
                            }
                            
                            Object rawValue = entryVar.getValue();
                            if (rawValue != null) {
                                java.lang.reflect.Type targetType = rv.getGenericType();
                                if (targetType == null) {
                                    targetType = (currentVal != null) ? currentVal.getClass() : Object.class;
                                }
                                
                                Object converted = objectMapper.convertValue(rawValue, objectMapper.constructType(targetType));
                                
                                if (currentVal instanceof com.ciro.jreactive.smart.SmartList currentList && converted instanceof java.util.List newList) {
                                    currentList.clear();
                                    @SuppressWarnings("unchecked")
                                    java.util.List<Object> safeList = (java.util.List<Object>) newList;
                                    currentList.addAll(safeList);
                                    restoredInstances.add(currentList);
                                } else if (currentVal instanceof com.ciro.jreactive.smart.SmartSet currentSet && converted instanceof java.util.Collection newCol) {
                                    currentSet.clear();
                                    @SuppressWarnings("unchecked")
                                    java.util.Collection<Object> safeCol = (java.util.Collection<Object>) newCol;
                                    currentSet.addAll(safeCol);
                                    restoredInstances.add(currentSet);
                                } else if (currentVal instanceof com.ciro.jreactive.smart.SmartMap currentMap && converted instanceof java.util.Map newMap) {
                                    currentMap.clear();
                                    @SuppressWarnings("unchecked")
                                    java.util.Map<Object, Object> safeMap = (java.util.Map<Object, Object>) newMap;
                                    currentMap.putAll(safeMap);
                                    restoredInstances.add(currentMap);
                                } else {
                                    rv.set(converted);
                                    // 🔥 FIX QUIRÚRGICO 3: No guardar primitivos en el escudo
                                    boolean isConvertedPrimitive = converted != null && (isPrimitiveOrWrapper(converted.getClass()) || converted instanceof String);
                                    if (!isConvertedPrimitive && converted != null) {
                                        restoredInstances.add(converted);
                                    }
                                }
                            } else {
                                rv.set(null);
                            }
                        }
                    }
                }
            } else if (owner instanceof HtmlComponent comp) {
                comp._captureStateSnapshot();
            }

            // B. Actualizar con los inputs que vienen en args
            autoUpdateStateFromArgs(owner, target, args);
            if (owner instanceof HtmlComponent comp) comp._syncState();

            // C. Ejecutar la acción
            Object result = null;
            if (owner instanceof HtmlComponent comp) {
                ComponentAccessor acc = AccessorRegistry.get(comp.getClass());
                if (acc != null) {
                    result = acc.call(comp, target.getName(), args);
                } else {
                    // Fallback seguro por si no hay Accessor
                    result = target.invoke(owner, args);
                }
            } else {
                result = target.invoke(owner, args);
            }
            
            
            if (owner instanceof HtmlComponent comp) comp._syncState();

            // D. Armar respuesta
            Map<String, Object> envelope = new HashMap<>();
            envelope.put("ok", true);
            if (result != null) envelope.put("result", result);

            if (isStateless) {
                Map<String, Object> newState = new HashMap<>();
                allBinds.forEach((k, v) -> newState.put(k, v.get()));

                // 🔥 Comparación exacta de Deltas usando JsonNode
                List<Map<String, Object>> batch = new java.util.ArrayList<>();
                for (Map.Entry<String, Object> entryVar : newState.entrySet()) {
                    String k = entryVar.getKey();
                    Object v = entryVar.getValue();
                    com.fasterxml.jackson.databind.JsonNode newTree = objectMapper.valueToTree(v);
                    com.fasterxml.jackson.databind.JsonNode oldTree = objectMapper.valueToTree(oldState.get(k));

                    if (!Objects.equals(newTree, oldTree)) {
                        batch.add(Map.of("k", k, "v", v));
                    }
                }
                
                envelope.put("newStateToken", JrxStateToken.encode(newState));
                if (!batch.isEmpty()) envelope.put("batch", batch);
            } else {
                Call callAnn = target.getAnnotation(Call.class);
                if (callAnn != null && callAnn.sync() && owner instanceof HtmlComponent comp) {
                    if (this.persistenceEnabled) pageResolver.persist(sessionId, path, page);
                }
            }

            return objectMapper.writeValueAsString(envelope);

        } catch (Exception e) {
            e.printStackTrace();
            
            // 🔥 CAZADOR DE ERRORES: Extraemos el error real, sin importar lo profundo que esté
            String msg = e.getMessage();
            if (e instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause() != null) {
                msg = ite.getCause().getClass().getSimpleName() + ": " + ite.getCause().getMessage();
                // Si el error interno tampoco tiene mensaje (ej: NullPointerException)
                if (ite.getCause().getMessage() == null) {
                    msg = ite.getCause().getClass().getSimpleName() + " en la línea " + ite.getCause().getStackTrace()[0].getLineNumber();
                }
            }
            
            // Fallback por si la excepción principal es un NPE
            if (msg == null) {
                msg = e.getClass().getSimpleName() + " en la línea " + e.getStackTrace()[0].getLineNumber();
            }
            
            return guard.errorJson("INVOKE_ERROR", "Error al invocar " + qualified + ": " + msg);
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
    
 // 🔥 NOTA: Añadimos 'Method method' a la firma
    private void autoUpdateStateFromArgs(Object owner, Method method, Object[] args) {
        if (owner == null || args == null || method == null) return;

        // Obtenemos los metadatos de los parámetros del método (nombres reales)
        java.lang.reflect.Parameter[] params = method.getParameters();

        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg == null) continue;

            // 1. Seguridad: Ignoramos tipos básicos para no sobrescribir contadores o flags
            if (isPrimitiveOrWrapper(arg.getClass()) || arg instanceof String) {
                continue;
            }

            // 🔥 LA CLAVE: Extraemos el nombre exacto del parámetro (Ej: "form")
            String targetName = params[i].getName();

            // 2. Inicio de la búsqueda en la clase del objeto
            Class<?> clazz = owner.getClass();
            boolean inyectado = false;

            // 3. Bucle para subir por la herencia (Proxy -> Clase Real -> Padre)
            while (clazz != null && clazz != Object.class) {
                try {
                    // 🔥 Búsqueda quirúrgica O(1): Buscamos el campo exactamente por su nombre
                    java.lang.reflect.Field field = clazz.getDeclaredField(targetName);
                    
                    // Solo inyectamos si está marcado como @State y los tipos coinciden
                    if (field.isAnnotationPresent(com.ciro.jreactive.State.class) &&
                        field.getType().isAssignableFrom(arg.getClass())) {
                        
                        field.setAccessible(true);
                        field.set(owner, arg); // 🔄 Inyección Quirúrgica
                        inyectado = true;
                        //System.out.println("✅ [JRX-BIND] Inyección segura por nombre en: " + field.getName() + " (Clase: " + clazz.getSimpleName() + ")");
                        break; 
                    }
                } catch (NoSuchFieldException e) {
                    // Silencioso: El campo no está en esta clase, el while subirá al padre
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
                
                if (inyectado) break;
                // ⬆️ Subimos un nivel en la jerarquía
                clazz = clazz.getSuperclass();
            }
            /*
            if (!inyectado) {
                System.err.println("⚠️ [JRX-BIND] No se inyectó el argumento '" + targetName + "' (No se encontró un @State con ese nombre exacto o los tipos no coinciden).");
            }
            */
        }
    }

    private boolean isPrimitiveOrWrapper(Class<?> type) {
        return type.isPrimitive() || 
               Number.class.isAssignableFrom(type) || 
               Boolean.class.isAssignableFrom(type) || 
               Character.class.isAssignableFrom(type);
    }
    
    
 // =========================================================================
    // HELPERS STATELESS
    // =========================================================================
    
    private void collectBindingsRecursive(HtmlComponent comp, Map<String, ReactiveVar<?>> all) {
        collectBindingsRecursive(comp, comp, all);
    }

    private void collectBindingsRecursive(HtmlComponent rootPage, HtmlComponent current, Map<String, ReactiveVar<?>> all) {
        // 🔥 FIX 2: Igualar el idioma para el Proxy JS
        if (current == rootPage) {
            String rootPrefix = current.getId() + ".";
            current.getRawBindings().forEach((k, v) -> {
                all.put(k, v);               // Acceso corto (ej: form.name) para lógica de backend
                all.put(rootPrefix + k, v);  // Acceso largo (ej: SignupPage2#1.form) para el render de JS
            });
        } else {
            // Hijos mantienen su namespace estricto
            String prefix = current.getId() + ".";
            current.getRawBindings().forEach((k, v) -> all.put(prefix + k, v));
        }
        
        for (HtmlComponent child : current._children()) {
            collectBindingsRecursive(rootPage, child, all);
        }
    }
    
    private String injectStatelessToken(String html, Map<String, ReactiveVar<?>> all, String rootIdOrNull) {
        try {
            Map<String, Object> tokenState = new HashMap<>();
            all.forEach((k, v) -> tokenState.put(k, v.get()));

            String token = JrxStateToken.encode(tokenState);
            String rawJson = JrxStateToken.toJson(tokenState);

            return "<meta name=\"jrx-state\" content=\"" + token + "\">\n" +
                   "<script>window.__JRX_STATE__ = " + rawJson +
                   "; window.__JRX_ROOT_ID__ = " + (rootIdOrNull == null ? "null" : ("'" + rootIdOrNull + "'")) +
                   ";</script>\n" + html;
        } catch (Exception e) {
            System.err.println("ℹ️ [JReactive] Fallback activado para inyección de Token Stateless.");
            return html;
        }
    }
    
    

    
    
}

