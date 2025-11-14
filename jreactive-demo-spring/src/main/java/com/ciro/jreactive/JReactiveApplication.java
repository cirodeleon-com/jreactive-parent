package com.ciro.jreactive;

import com.ciro.jreactive.router.Param;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootApplication
public class JReactiveApplication {

    public static void main(String[] args) {
        SpringApplication.run(JReactiveApplication.class, args);
    }

    @RestController
    class PageController {

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

        /* Acepta:
           - "/"
           - "/foo"
           - "/foo/bar/baz"  (cualquier profundidad)
           Evita js|ws al inicio para no pisar recursos estáticos ni el endpoint WS.
        */
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
            try {
                guard.validateParams(owner, target, args);
            } catch (IllegalArgumentException ex) {
                return guard.errorJson("VALIDATION", ex.getMessage());
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

        /* Recolecta todos los métodos @Call del árbol de componentes */
        private Map<String, Map.Entry<Method, HtmlComponent>> collectCallables(HtmlComponent root) {
            Map<String, Map.Entry<Method, HtmlComponent>> map = new HashMap<>();
            String compId = root.getId();

            // propios
            for (var e : root.getCallableMethods().entrySet()) {
                String key = compId + "." + e.getKey();
                map.put(key, Map.entry(e.getValue(), root));
            }
            // hijos
            for (HtmlComponent child : root._children()) {
                map.putAll(collectCallables(child));
            }
            return map;
        }
    }
}
