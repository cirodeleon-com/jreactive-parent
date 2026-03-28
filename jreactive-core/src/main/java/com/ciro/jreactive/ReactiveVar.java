package com.ciro.jreactive;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

public final class ReactiveVar<T> implements java.io.Serializable {
    private T value;
    
    // CopyOnWriteArrayList es Thread-Safe por naturaleza para iteraciones, 
    // ideal aquí porque los listeners cambian poco pero se leen mucho.
    private final List<Consumer<T>> listeners = new CopyOnWriteArrayList<>();
    
    private volatile BooleanSupplier activeGuard = () -> true;
    
    private transient java.lang.reflect.Type genericType;
    private transient String sharedTopic = null;
    public java.lang.reflect.Type getGenericType() { return genericType; }
    public void setGenericType(java.lang.reflect.Type genericType) { this.genericType = genericType; }

    public ReactiveVar(T initial) { this.value = initial; }

    public T get() { return value; }

    public void set(T newValue) {
        this.value = newValue;
        
        if (!activeGuard.getAsBoolean()) {
            return;
        }
        
        // 🚀 Disparamos los eventos libres de bloqueos (Protegidos por la cola JRX)
        for (Consumer<T> listener : listeners) {
            listener.accept(newValue);
        }
    }
    
    public void setSilent(T newValue) {
        this.value = newValue;
        // 🛑 NO disparamos los listeners
    }
    
    public void setActiveGuard(BooleanSupplier guard) {
        this.activeGuard = (guard != null) ? guard : () -> true;
    }
    
    public void clearListeners() {
        listeners.clear();
    }

    public Runnable onChange(Consumer<T> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }
    
    
    public String getSharedTopic() { return sharedTopic; }
    public void setSharedTopic(String topic) { this.sharedTopic = topic; }
}