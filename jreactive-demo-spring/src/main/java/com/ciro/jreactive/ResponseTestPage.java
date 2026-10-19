package com.ciro.jreactive;

import com.ciro.jreactive.router.Route;
import com.ciro.jreactive.router.UrlParam;

/**
 * Banco de pruebas de HttpPage: la página decide su código de estado y sus cabeceras.
 *
 * <ul>
 *   <li>/response-test: 200.</li>
 *   <li>/response-test?estado=espera: 202, con el mensaje "Aún procesando".</li>
 *   <li>/response-test?estado=falta: 404, con la página de error de Spring.</li>
 * </ul>
 * Las respuestas 200 y 202 llevan además Referrer-Policy: no-referrer.
 */
@Route(path = "/response-test")
public class ResponseTestPage extends HtmlComponent implements HttpPage {

    @UrlParam("estado")
    @State
    public String estado = "";

    @State
    public String mensaje = "Respuesta normal";

    @Override
    public void onRequest(JrxResponse response) {
        if ("falta".equals(estado)) {
            throw new JrxHttpException(404, "No existe");
        }
        if ("espera".equals(estado)) {
            response.status(202);
            mensaje = "Aún procesando";
        }
        response.header("Referrer-Policy", "no-referrer");
    }

    @Override
    protected String template() {
        return """
            <section>
                <h1>{{mensaje}}</h1>
                <p>Estado pedido: {{estado}}</p>
            </section>
            """;
    }
}