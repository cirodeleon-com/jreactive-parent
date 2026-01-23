package com.ciro.jreactive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "jreactive.ws")
public class WsConfig {
    /** Activa/desactiva el escudo de back-pressure */
    private boolean enabledBackpressure = true;
    /** Límite de eventos en cola antes de descartar */
    private int maxQueue = 512;
    /** Intervalo de flush en milisegundos */
    private int flushIntervalMs = 16;
    
    private boolean persistentState = true;

    public boolean isEnabledBackpressure() { return enabledBackpressure; }
    public void setEnabledBackpressure(boolean enabledBackpressure) { this.enabledBackpressure = enabledBackpressure; }

    public int getMaxQueue() { return maxQueue; }
    public void setMaxQueue(int maxQueue) { this.maxQueue = maxQueue; }

    public int getFlushIntervalMs() { return flushIntervalMs; }
    public void setFlushIntervalMs(int flushIntervalMs) { this.flushIntervalMs = flushIntervalMs; }
    
    public boolean isPersistentState() { return persistentState; }
    public void setPersistentState(boolean persistentState) { this.persistentState = persistentState; }
}
