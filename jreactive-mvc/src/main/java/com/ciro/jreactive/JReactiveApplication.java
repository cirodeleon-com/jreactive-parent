package com.ciro.jreactive;


import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Parameter; 
import jakarta.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.databind.JavaType;   // ← nuevo


@SpringBootApplication
public class JReactiveApplication {

	

    public static void main(String[] args) {
        SpringApplication.run(JReactiveApplication.class, args);
    }

    

    @RestController
    class PageController {

        private final PageResolver pageResolver;
        private final ObjectMapper objectMapper;


        public PageController(PageResolver pageResolver,ObjectMapper objectMapper) {
            this.pageResolver = pageResolver;
            this.objectMapper = objectMapper;
        }

        @GetMapping(value = {"/", "/{path:^(?!js|ws).*$}"}, produces = MediaType.TEXT_HTML_VALUE)
        public String page(HttpServletRequest req,
                           @RequestHeader(value = "X-Partial", required = false) String partial) {

            HtmlComponent page = pageResolver.getPage(req.getRequestURI());
            String html = page.render();

            // Si es navegación interna, solo mandamos el fragmento
            if (partial != null) return html;

            // Si es carga inicial (sin X-Partial), mandamos layout completo
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
        	    value = "/call/{qualified:.+}",           // ← aquí el .+ permite puntos y escapes
        	    consumes = MediaType.APPLICATION_JSON_VALUE,
        	    produces = MediaType.APPLICATION_JSON_VALUE
        	)
        	public String callMethod(
        	      @PathVariable("qualified") String qualified,
        	      @RequestBody Map<String,Object> body,
        	      HttpServletRequest req
        	) {
        	    // 1) Reconstruyo la página
        	    String path = req.getHeader("Referer")
        	                     .replaceFirst("https?://[^/]+", "");
        	    HtmlComponent page = pageResolver.getPage(path);

        	    // 2) Busco el método completo "compId.metodo"
        	    var callables = collectCallables(page);
        	    var entry     = callables.get(qualified);
        	    if (entry == null) {
        	        return "{\"error\":\"Método no permitido: " + qualified + "\"}";
        	    }
        	    Method target = entry.getKey();
        	    Object owner  = entry.getValue();

        	    // 3) Deserializo args
        	    @SuppressWarnings("unchecked")
        	    List<Object> rawArgs = (List<Object>) body.getOrDefault("args", List.of());
        	    Parameter[] params   = target.getParameters();
        	    Object[] args        = new Object[params.length];
        	    for (int i = 0; i < params.length; i++) {
        	        Object raw = i < rawArgs.size() ? rawArgs.get(i) : null;
        	        JavaType type = objectMapper.getTypeFactory()
        	                                    .constructType(params[i].getParameterizedType());
        	        args[i] = objectMapper.convertValue(raw, type);
        	    }

        	    // 4) Invoco y devuelvo
        	    try {
        	        Object result = target.invoke(owner, args);
        	        return (result == null)
        	             ? ""
        	             : objectMapper.writeValueAsString(result);
        	    } catch (Exception e) {
        	        e.printStackTrace();
        	        return "{\"error\":\"Error al invocar " + qualified 
        	             + ": " + e.getMessage() + "\"}";
        	    }
        	}


        
        
        private Map<String, Map.Entry<Method, HtmlComponent>> collectCallables(HtmlComponent root) {
            Map<String, Map.Entry<Method, HtmlComponent>> map = new HashMap<>();
            String compId = root.getId();  // puede ser tu ref o HelloLeaf#1

            // 1) propios
            for (var e : root.getCallableMethods().entrySet()) {
                String key = compId + "." + e.getKey();
                map.put(key, Map.entry(e.getValue(), root));
            }
            // 2) hijos recursivos
            for (HtmlComponent child : root._children()) {
                map.putAll(collectCallables(child));
            }
            return map;
        }






    }


}
