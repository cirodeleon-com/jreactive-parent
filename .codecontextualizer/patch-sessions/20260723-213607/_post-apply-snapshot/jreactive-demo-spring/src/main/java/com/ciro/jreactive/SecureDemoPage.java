package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Stateful;
import com.ciro.jreactive.router.Route;

@Route(path = "/secure-demo")
@Stateful
public class SecureDemoPage extends AppPage {

    @Override
    protected String template() {
        return """
            <div style="padding: 40px; font-family: system-ui, sans-serif; max-width: 600px; margin: 0 auto;">
                <h1>Demo Segura</h1>
                <p style="color: #666;">Página base para demostrar el sistema de autorización.</p>
            </div>
        """;
    }
}