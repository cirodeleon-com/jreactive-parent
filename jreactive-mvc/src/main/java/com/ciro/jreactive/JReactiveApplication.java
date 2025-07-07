package com.ciro.jreactive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ciro.jreactive.router.RouteRegistry;

import jakarta.servlet.http.HttpServletRequest;

@SpringBootApplication
public class JReactiveApplication {

	

    public static void main(String[] args) {
        SpringApplication.run(JReactiveApplication.class, args);
    }

    

    @RestController          // usa Lombok o constructor manual
    class PageController {
        private final PageResolver pageResolver;
        
        public PageController(PageResolver pageResolver) {
        	this.pageResolver=pageResolver;
        }

        @GetMapping(value = {"/", "/{path:^(?!js|ws).*$}"}, produces = MediaType.TEXT_HTML_VALUE)
        public String page(HttpServletRequest req) {
        	HtmlComponent page = pageResolver.getPage(req.getRequestURI());
            return """
                <!DOCTYPE html><html><head><meta charset='UTF-8'></head><body>
                  %s
                  <script src='/js/jreactive-runtime.js'></script>
                </body></html>
                """.formatted(page.render());
        }
    }

}
