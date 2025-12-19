package com.ciro.jreactive.spi;

public interface JrxSession {
    String getId();
    boolean isOpen();
    void sendText(String json);
    void close();
    // Para guardar atributos (como 'path' o 'delegate')
    void setAttr(String key, Object val);
    Object getAttr(String key);
}