package com.ciro.jreactive;

import org.springframework.stereotype.Component;
import com.ciro.jreactive.router.Route;

@Component
@Route(path = "/hook-test")
public class HookPage extends AppPage {

    @Override
    protected String template() {
        return """
            <h1>Prueba Local (Paquete)</h1>

            <div 
                client:mount="HookPage_mount(this)" 
                client:unmount="HookPage_unmount(this)"
            >
                Soy un componente con JS encapsulado.
            </div>
            
            <hr/>
            <a href="/" data-router>⬅️ Volver</a>
        """;
    }
}