package com.ciro.jreactive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

@SpringBootApplication
public class JReactiveApplication {

	

    public static void main(String[] args) {
        SpringApplication.run(JReactiveApplication.class, args);
    }

    

    @RestController
    class PageController {

        private final PageResolver pageResolver;

        public PageController(PageResolver pageResolver) {
            this.pageResolver = pageResolver;
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
    }


}
