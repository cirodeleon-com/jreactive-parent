package com.ciro.jreactive;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class PageController {

    private final PageResolver pageResolver; // (Opcional mantenerlo si no se usa aquí, pero JrxHttpApi lo usa internamente)
    private final JrxHttpApi api;

    public PageController(PageResolver pageResolver,
                          ObjectMapper objectMapper,
                          CallGuard guard) {
        this.pageResolver = pageResolver;
        this.api = new JrxHttpApi(pageResolver, objectMapper, guard);
    }

    @GetMapping(value = {
            "/",
            "/{x:^(?!js|ws|css|static).*$}",        // He añadido 'static' por si acaso sirves css/img
            "/{x:^(?!js|ws|css|static).*$}/**"
    }, produces = MediaType.TEXT_HTML_VALUE)
    public String page(HttpServletRequest req,
                       @RequestHeader(value = "X-Partial", required = false) String partial) {
        
        // 1. Capturar contexto
        String path = req.getRequestURI();
        String sessionId = req.getSession(true).getId();

        // 2. Lógica de SPA:
        // Si X-Partial == "1" (viene del JS) -> renderLayout = false
        // Si es carga normal (viene del navegador) -> renderLayout = true
        boolean renderLayout = !"1".equals(partial);

        // 3. Delegación total:
        // La API buscará el componente y su @Layout si corresponde.
        return api.render(sessionId, path, renderLayout);
    }

    @PostMapping(
            value = "/call/{qualified:.+}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public String callMethod(@PathVariable("qualified") String qualified,
                             @RequestBody Map<String, Object> body,
                             HttpServletRequest req) {

        System.out.println("➡️ JRX CALL qualified=" + qualified + " body=" + body);

        String ref = req.getHeader("Referer");
        String path = (ref == null) ? "/" : ref.replaceFirst("https?://[^/]+", "");

        int q = path.indexOf('?');
        if (q != -1) path = path.substring(0, q);
        int hash = path.indexOf('#');
        if (hash != -1) path = path.substring(0, hash);

        String sessionId = req.getSession(true).getId();

        return api.call(sessionId, path, qualified, body);
    }
}