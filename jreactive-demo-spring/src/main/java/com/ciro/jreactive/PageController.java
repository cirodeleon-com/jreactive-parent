package com.ciro.jreactive;

import com.ciro.jreactive.router.Param;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class PageController {

    private final PageResolver  pageResolver;
    private final ObjectMapper  objectMapper;
    private final CallGuard     guard;

    public PageController(PageResolver pageResolver,
                          ObjectMapper objectMapper,
                          CallGuard guard) {
        this.pageResolver = pageResolver;
        this.objectMapper = objectMapper;
        this.guard        = guard;
    }

    @GetMapping(value = {
            "/",
            "/{x:^(?!js|ws).*$}",
            "/{x:^(?!js|ws).*$}/**"
    }, produces = MediaType.TEXT_HTML_VALUE)
    public String page(HttpServletRequest req,
                       @RequestHeader(value = "X-Partial", required = false) String partial) {
        return render(req, partial);
    }

    /** Lógica común de render. */
    private String render(HttpServletRequest req, String partial) {
        String path = req.getRequestURI();
        String sessionId = req.getSession(true).getId();

        HtmlComponent page = pageResolver.getPage(sessionId, path);
        String html = page.render();

        if (partial != null) return html;   // fragmento para la SPA

        return """
        <!DOCTYPE html>
        <html>
          <head><meta charset="UTF-8"><title>JReactive Demo</title></head>
          <body>
            <header><a data-router href="/">🏠 Home</a> | <a data-router href="/other">📄 Other</a></header>
            <main id="app">%s</main>
            <footer><label>este es el fooooter</label></footer>
            <script src="/js/jreactive-runtime.js"></script>
          </body>
        </html>
        """.formatted(html);
    }

    @PostMapping(
            value     = "/call/{qualified:.+}",
            consumes  = MediaType.APPLICATION_JSON_VALUE,
            produces  = MediaType.APPLICATION_JSON_VALUE
    )
    public String callMethod(@PathVariable("qualified") String qualified,
                             @RequestBody Map<String, Object> body,
                             HttpServletRequest req) {
    	
    	 System.out.println("➡️ JRX CALL qualified=" + qualified + " body=" + body);

        // 1) reconstruir la página desde el Referer (fallback "/")
        String ref  = req.getHeader("Referer");
        String path = (ref == null) ? "/" : ref.replaceFirst("https?://[^/]+", "");

        // 🔹 Normalizar: quitar query (?...) y hash (#...)
        int q = path.indexOf('?');
        if (q != -1) {
            path = path.substring(0, q);
        }
        int hash = path.indexOf('#');
        if (hash != -1) {
            path = path.substring(0, hash);
        }

        String sessionId = req.getSession(true).getId();

        HtmlComponent page = pageResolver.getPage(sessionId, path);

        // 2) localizar método "CompId.metodo"
        var callables = collectCallables(page);
        var entry     = callables.get(qualified);
        if (entry == null) {
            return guard.errorJson("NOT_FOUND",
                    "Método no permitido: " + qualified);
        }
        Method target = entry.getKey();
        Object owner  = entry.getValue();

        // 3) deserializar args (mezcla body + @Param del path)
        @SuppressWarnings("unchecked")
        List<Object> rawArgs = (List<Object>) body.getOrDefault("args", List.of());
        Parameter[] params   = target.getParameters();
        Object[] args        = new Object[params.length];

        Map<String,String> routeParams = pageResolver.getParams(sessionId,path);
        if (routeParams == null) routeParams = Map.of();

        for (int i = 0; i < params.length; i++) {
            Parameter p = params[i];
            Object raw  = i < rawArgs.size() ? rawArgs.get(i) : null;

            Param ann = p.getAnnotation(Param.class);
            if (ann != null) {
                raw = routeParams.get(ann.value());
            }

            JavaType type = objectMapper.getTypeFactory()
                    .constructType(p.getParameterizedType());
            args[i] = objectMapper.convertValue(raw, type);
        }

        // 4-a) rate limit (por sesión + método)
        String rateKey = sessionId + ":" + qualified;
        if (!guard.tryConsume(rateKey)) {
            return guard.errorJson("RATE_LIMIT",
                    "Demasiadas llamadas, inténtalo en un instante");
        }

        // 4-b) Bean Validation
        var violations = guard.validateParams(owner, target, args);
        if (!violations.isEmpty()) {
            // devolvemos JSON estándar de validación
            return guard.validationJson(violations);
        }

        // 5) invocar
        try {
            Object result = target.invoke(owner, args);

            Map<String,Object> envelope = new HashMap<>();
            envelope.put("ok", true);
            if (result != null) {
                envelope.put("result", result);
            }
            return objectMapper.writeValueAsString(envelope);

        } catch (Exception e) {
            e.printStackTrace();
            return guard.errorJson(
                    "INVOKE_ERROR",
                    "Error al invocar " + qualified + ": " + e.getMessage()
            );
        }
    }

    /**
     * Recoge todos los métodos @Call visibles para una página.
     *
     * Regla:
     *  - La página raíz expone:
     *      • "PageId.metodo"  (siempre)
     *      • "metodo"         (nombre corto, sólo en la raíz)
     *  - Los componentes hijos SÓLO exponen:
     *      • "CompId.metodo"
     *
     * Así evitamos colisiones de nombres cortos entre hijos.
     */
    private Map<String, Map.Entry<Method, HtmlComponent>> collectCallables(HtmlComponent rootPage) {
        Map<String, Map.Entry<Method, HtmlComponent>> map = new HashMap<>();
        collectCallables(rootPage, rootPage, map);
        return map;
    }

    private void collectCallables(HtmlComponent rootPage,
                                  HtmlComponent current,
                                  Map<String, Map.Entry<Method, HtmlComponent>> map) {

        String compId = current.getId();

        for (var e : current.getCallableMethods().entrySet()) {
            String methodName = e.getKey();
            Method m          = e.getValue();

            // 1) Clave completa SIEMPRE (HelloLeaf#1.addFruit, NewStateTestPage#1.addItem, etc.)
            map.put(compId + "." + methodName, Map.entry(m, current));

            // 2) Clave corta SOLO si es la página raíz
            if (current == rootPage) {
                map.put(methodName, Map.entry(m, current));
            }
        }

        // Recorremos hijos recursivamente, pero SIN registrar nombres cortos
        for (HtmlComponent child : current._children()) {
            collectCallables(rootPage, child, map);
        }
    }

}
