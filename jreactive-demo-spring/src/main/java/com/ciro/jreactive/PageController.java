package com.ciro.jreactive;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class PageController {

    private final PageResolver pageResolver;
    private final JrxHttpApi api;

    public PageController(PageResolver pageResolver,
                          ObjectMapper objectMapper,
                          CallGuard guard) {
        this.pageResolver = pageResolver;
        this.api = new JrxHttpApi(pageResolver, objectMapper, guard);
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

        // Mantener comportamiento actual (aunque evict hoy sea NO-OP)
        if (partial == null) {
            pageResolver.evict(sessionId, path);
        }

        String html = api.render(sessionId, path);

        if (partial != null) return html;

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
            value = "/call/{qualified:.+}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public String callMethod(@PathVariable("qualified") String qualified,
                             @RequestBody Map<String, Object> body,
                             HttpServletRequest req) {

        System.out.println("➡️ JRX CALL qualified=" + qualified + " body=" + body);

        // reconstruir la página desde el Referer (fallback "/")
        String ref = req.getHeader("Referer");
        String path = (ref == null) ? "/" : ref.replaceFirst("https?://[^/]+", "");

        // Normalizar: quitar query y hash
        int q = path.indexOf('?');
        if (q != -1) path = path.substring(0, q);
        int hash = path.indexOf('#');
        if (hash != -1) path = path.substring(0, hash);

        String sessionId = req.getSession(true).getId();

        return api.call(sessionId, path, qualified, body);
    }
}
