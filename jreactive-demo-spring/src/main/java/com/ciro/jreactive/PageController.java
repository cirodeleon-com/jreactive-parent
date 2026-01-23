package com.ciro.jreactive;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class PageController {

    private final PageResolver pageResolver; // (puede quedarse)
    private final JrxHttpApi api;
    private final JrxRequestQueue queue;

    public PageController(PageResolver pageResolver,
                          ObjectMapper objectMapper,
                          CallGuard guard,
                          WsConfig wsConfig,
                          JrxRequestQueue queue) {
        this.pageResolver = pageResolver;
        this.api = new JrxHttpApi(pageResolver, objectMapper, guard, wsConfig.isPersistentState());
        this.queue = queue;
    }

    @GetMapping(value = {
            "/",
            "/{x:^(?!js|ws|css|static).*$}",
            "/{x:^(?!js|ws|css|static).*$}/**"
    }, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> page(HttpServletRequest req,
                                       @RequestHeader(value = "X-Partial", required = false) String partial) {

        String path = req.getRequestURI();
        String sessionId = req.getSession(true).getId();

        // SPA: X-Partial == "1" => renderLayout=false
        boolean renderLayout = !"1".equals(partial);

        // ✅ serializamos también render por si hay rutas que disparan timers/state a la par de eventos
        String html = queue.run(sessionId, path, () -> api.render(sessionId, path, renderLayout));

        return ResponseEntity.ok()
                .header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .body(html);
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

        String sessionId = req.getSession(true).getId();
        String path = extractPath(req); // ✅ helper robusto

        // ✅ Cola por (sessionId+path): orden garantizado entre set/call/call
        return queue.run(sessionId, path, () -> api.call(sessionId, path, qualified, body));
    }

    private String extractPath(HttpServletRequest req) {
        // ✅ Preferido: si el JS algún día manda header X-Path (no rompe nada)
        String xPath = req.getHeader("X-Path");
        if (xPath != null && !xPath.isBlank()) return strip(xPath);

        // Fallback: Referer
        String ref = req.getHeader("Referer");
        String path = (ref == null) ? "/" : ref.replaceFirst("https?://[^/]+", "");
        return strip(path);
    }

    private String strip(String path) {
        if (path == null || path.isBlank()) return "/";

        int q = path.indexOf('?');
        if (q != -1) path = path.substring(0, q);

        int hash = path.indexOf('#');
        if (hash != -1) path = path.substring(0, hash);

        return path.isBlank() ? "/" : path;
    }
}
