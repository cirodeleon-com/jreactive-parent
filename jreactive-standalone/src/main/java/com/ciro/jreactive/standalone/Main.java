package com.ciro.jreactive.standalone;

import com.ciro.jreactive.HtmlComponent;
import com.ciro.jreactive.JsoupComponentEngine;
import com.ciro.jreactive.State;
import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.annotations.Client;
import com.ciro.jreactive.router.Route;

public class Main {

    public static void main(String[] args) {
    	
    	JsoupComponentEngine.installAsDefault();

        System.out.println("🚀 Iniciando servidor JReactive Standalone...");
    	
        int port = 8080;

        JReactiveServer server = new JReactiveServer(port);

        server.addRoute("/", CounterPage::new);

        System.out.println("⏳ Iniciando servidor JReactive Standalone...");
        server.start();
    }
    //@Route(path = "/")
    public static class CounterPage extends HtmlComponent {

        @State
        public int count = 0;

        @Call
        public void increment() {
            count++;
        }

        @Override
        protected String template() {
            return """
                <div style="font-family: system-ui, sans-serif; text-align: center; padding: 50px;">
                    <h1>🚀 JReactive Standalone</h1>
                    <p>Corriendo sobre <strong>Undertow</strong> (Sin Spring Boot)</p>

                    <div style="border: 1px solid #ccc; padding: 20px; display: inline-block; border-radius: 10px;">
                        <h2>Contador: {{count}}</h2>

                        <button
                            @click="increment()"
                            style="padding: 10px 18px; font-size: 1.1em; cursor: pointer;"
                        >
                            ¡Click me!
                        </button>
                    </div>

                    <p style="opacity: .7; margin-top: 22px;">
                        Prueba de router: <a data-router href="/" style="text-decoration: underline;">/</a>
                    </p>
                </div>
            """;
        }
    }
}
