package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.annotations.Stateless;
import com.ciro.jreactive.router.Layout;
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
    public String render(String sessionId, String path, boolean renderLayout) {
        HtmlComponent page = pageResolver.getPage(sessionId, path);
        
        // 1. Si es petición parcial (AJAX/SPA), devolvemos solo la página
        if (!renderLayout) {
            return page.render();
        }

        // 2. Si es carga completa, buscamos si tiene @Layout
        Layout layoutAnn = page.getClass().getAnnotation(Layout.class);
        
        if (layoutAnn != null) {
            try {
                // Creamos una instancia fresca del Layout
                // (Nota: En una versión futura podríamos cachearlo o inyectarlo con Spring)
                HtmlComponent layout = layoutAnn.value().getDeclaredConstructor().newInstance();
                
                // 🔥 Inyección: Renderizamos la página y se la pasamos al layout como slot
                layout._setSlotHtml(page.render());
                
                // Renderizamos el layout (que ahora contiene la página adentro)
                return layout.render();
                
            } catch (Exception e) {
                e.printStackTrace();
                // Si falla el layout, devolvemos la página "cruda" como fallback
                return page.render();
            }
        }

        // 3. Si no tiene layout, se devuelve cruda (útil para popups o páginas simples)
        return page.render();
    }

    /** Ejecuta un @Call (qualified = "CompId.metodo" o "metodo" en raíz) */
    public String call(String sessionId, String path, String qualified, Map<String, Object> body) {

        HtmlComponent page = pageResolver.getPage(sessionId, path);
        
        if (page._state() == ComponentState.UNMOUNTED) {
            System.out.println("♻️ [JrxHttpApi] Página no montada detectada. Hidratando árbol para: " + qualified);
            page.render();
        }
        
        

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
        
        autoUpdateStateFromArgs(owner, args);

        // 5) invocar
        try {
            if (owner instanceof HtmlComponent comp) {
            	
            	if (comp._state() == ComponentState.UNMOUNTED) {
                    comp._initIfNeeded();
                    comp._mountRecursive();
                }
            	
            	//if (!comp.getClass().isAnnotationPresent(Stateless.class)) {
                   comp._captureStateSnapshot();
            	//}
            }

            Object result = target.invoke(owner, args);

            Call callAnn = target.getAnnotation(Call.class);
            if (callAnn != null && callAnn.sync() && owner instanceof HtmlComponent comp) {
                comp._syncState();
                if (this.persistenceEnabled) {
                   pageResolver.persist(sessionId, path, page);
                }
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
    
    /**
     * Magia de Framework:
     * Si recibes un objeto complejo (DTO) como argumento, busca si hay un campo @State
     * del mismo tipo en el componente y actualízalo automáticamente.
     */
    /**
     * Magia de Framework: Auto-Binding de DTOs.
     * 🔥 MODIFICADO: Ahora soporta Proxies (CGLIB) recorriendo la jerarquía.
     * Esto no afecta a componentes normales, simplemente busca más a fondo si no encuentra el campo al principio.
     */
    private void autoUpdateStateFromArgs(Object owner, Object[] args) {
        if (owner == null || args == null) return;

        for (Object arg : args) {
            if (arg == null) continue;

            // 1. Seguridad: Ignoramos tipos básicos para no sobrescribir contadores o flags por error
            if (isPrimitiveOrWrapper(arg.getClass()) || arg instanceof String) {
                continue;
            }

            // 2. Inicio de la búsqueda en la clase del objeto
            Class<?> clazz = owner.getClass();
            boolean inyectado = false;

            // 3. Bucle para subir por la herencia (Proxy -> Clase Real -> Padre)
            while (clazz != null && clazz != Object.class) {
                
                for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                    // Solo nos interesan los campos marcados como Estado
                    if (field.isAnnotationPresent(com.ciro.jreactive.State.class)) {
                        
                        // Si el tipo del campo es compatible con el argumento recibido
                        if (field.getType().isAssignableFrom(arg.getClass())) {
                            try {
                                field.setAccessible(true);
                                field.set(owner, arg); // 🔄 Inyección del valor
                                inyectado = true;
                                System.out.println("✅ [JRX-BIND] Inyección exitosa en: " + field.getName() + " (Clase: " + clazz.getSimpleName() + ")");
                                // Opcional: Break aquí si solo quieres bindear el primero que encuentres
                                // para evitar asignar el mismo DTO a dos campos diferentes.
                            } catch (IllegalAccessException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
                
                // ⬆️ Subimos un nivel en la jerarquía
                clazz = clazz.getSuperclass();
            }
            
            if (!inyectado) {
                System.err.println("⚠️ [JRX-BIND] No se encontró campo @State compatible para el argumento: " + arg.getClass().getSimpleName());
                System.err.println("   -> Buscado en jerarquía de: " + owner.getClass().getName());
            }
        }
    }

    private boolean isPrimitiveOrWrapper(Class<?> type) {
        return type.isPrimitive() || 
               Number.class.isAssignableFrom(type) || 
               Boolean.class.isAssignableFrom(type) || 
               Character.class.isAssignableFrom(type);
    }
}

