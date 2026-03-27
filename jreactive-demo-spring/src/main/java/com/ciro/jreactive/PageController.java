package com.ciro.jreactive;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
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
                          JrxRequestQueue queue,JrxHubManager hubManager) {
        this.pageResolver = pageResolver;
        this.api = new JrxHttpApi(pageResolver, objectMapper, guard, wsConfig.isPersistentState(),hubManager);
        this.queue = queue;
    }

    @GetMapping(value = {
            "/",
            "/{x:^(?!js|ws|css|static|jrx).*$}",
            "/{x:^(?!js|ws|css|static|jrx).*$}/**"
    }, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> page(HttpServletRequest req,
                                       @RequestHeader(value = "X-Partial", required = false) String partial) {

        String path = req.getRequestURI();
        String sessionId = req.getSession(true).getId();

        // SPA: X-Partial == "1" => renderLayout=false
        boolean renderLayout = !"1".equals(partial);
        
        Map<String, String> queryParams = new HashMap<>();
        req.getParameterMap().forEach((k, v) -> {
            if (v != null && v.length > 0) queryParams.put(k, v[0]);
        });

        // ✅ serializamos también render por si hay rutas que disparan timers/state a la par de eventos
        String html = queue.run(sessionId, path, () -> api.render(sessionId, path, renderLayout, queryParams));

        return ResponseEntity.ok()
                .header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .body(html);
    }

    @PostMapping(
            value = "/call/{qualified:.+}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE
    )
    public String callMethod(@PathVariable("qualified") String qualified,
                             @RequestBody Map<String, Object> body,
                             HttpServletRequest req) {

        System.out.println("➡️ JRX CALL qualified=" + qualified + " body=" + body);

        String sessionId = req.getSession(true).getId();
        String path = extractPath(req); // ✅ helper robusto
        
        Map<String, String> queryParams = new HashMap<>();
        req.getParameterMap().forEach((k, v) -> {
            if (v != null && v.length > 0) queryParams.put(k, v[0]);
        });

        // ✅ Cola por (sessionId+path): orden garantizado entre set/call/call
        return queue.run(sessionId, path, () -> api.call(sessionId, path, qualified, body, queryParams));
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
    
    
 // 🔥 La nueva tubería pesada (HTTP Multipart)
    @PostMapping(value = "/jrx/upload", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> handleUpload(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        try {
            // 1. Generar ID único y ruta en la carpeta temporal del sistema operativo
            String fileId = java.util.UUID.randomUUID().toString();
            java.io.File tempFile = java.io.File.createTempFile("jrx_" + fileId, ".tmp");
            
            // 2. Guardar directo a disco (¡Fuera de la RAM de Java!)
            file.transferTo(tempFile);

            // 3. Devolver la "mochila" JSON que el JS empujará por el WebSocket
            return Map.of(
                "fileId", fileId,
                "name", file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown",
                "contentType", file.getContentType() != null ? file.getContentType() : "application/octet-stream",
                "size", file.getSize(),
                "tempPath", tempFile.getAbsolutePath()
            );
        } catch (Exception e) {
            throw new RuntimeException("Error subiendo archivo en JReactive", e);
        }
    }
    
}
