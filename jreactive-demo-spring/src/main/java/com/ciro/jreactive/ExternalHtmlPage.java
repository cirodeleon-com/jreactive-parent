package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.annotations.Client;
import com.ciro.jreactive.router.Route;

@Route(path = "/external-test")
@Client
public class ExternalHtmlPage extends AppPage {
	// hi world

    @State
    public int clicks = 0;

    @State
    public String message = "Esperando acción...";

    @Call
    public void interact() {
        clicks++;
        message = "¡El HTML externo funciona a la perfección! 🚀";
    }
    
    // ¡MIRA MAMÁ, SIN MÉTODO template()! 😎
    // JReactive buscará automáticamente "ExternalHtmlPage.html"
}