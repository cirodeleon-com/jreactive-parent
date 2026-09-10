package com.ciro.jreactive;

/**
 * Publica señales efímeras destinadas a páginas JReactive vivas.
 */
@FunctionalInterface
public interface JrxEvents {

    void publish(String event);
}