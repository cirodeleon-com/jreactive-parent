package com.ciro.jreactive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ciro.jreactive.router.RouteRegistry;

@SpringBootApplication
public class JReactiveApplication {

	private final HomePage root = new HomePage();

    public static void main(String[] args) {
        SpringApplication.run(JReactiveApplication.class, args);
    }

    public HomePage getRoot() { return root; }

    @RestController
    class PageController {
    	
    	
    	private final RouteRegistry registry;
    	
    	public PageController(RouteRegistry registry) {
    		this.registry=registry;
    	}
    	
    	

        @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
        public String index() {
            return """
                <!DOCTYPE html>
                <html>
                  <head><meta charset=\"UTF-8\"/></head>
                  <body>
                    %s
                    <script src="/js/jreactive-runtime.js"></script>
                  </body>
                </html>
                """.formatted(root.render());
        }
    }
}
