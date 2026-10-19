package com.ciro.jreactive;

/**
 * Página que decide su propia respuesta HTTP.
 *
 * <p>Una página con {@code @Route} la implementa cuando necesita, en cada petición, cargar lo que
 * va a mostrar, fijar el código de estado y las cabeceras, o cortar la petición con
 * {@link JrxHttpException}. Las páginas que no la implementan responden como siempre.
 */
public interface HttpPage {

    /**
     * Se ejecuta en cada petición HTTP que pide esta página, completa o por navegación parcial:
     * después de onInit y antes de renderizar. Los @Call y el WebSocket no pasan por aquí.
     */
    void onRequest(JrxResponse response);
}