package com.ciro.jreactive;

/**
 * Corta la petición de una página y responde con ese código de estado, sin renderizarla.
 *
 * <p>Se lanza desde {@link HttpPage#onRequest(JrxResponse)}. En Spring, la aplicación
 * responde con su página de error habitual para ese código.
 */
public class JrxHttpException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;

    public JrxHttpException(int status) {
        this(status, null);
    }

    public JrxHttpException(int status, String reason) {
        super(reason);
        this.status = JrxResponse.requireStatus(status);
    }

    public int status() {
        return status;
    }
}