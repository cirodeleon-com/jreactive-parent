package com.ciro.jreactive;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.concurrent.locks.ReentrantLock;

public final class ReactiveVar<T> implements java.io.Serializable {
    private T value;
    private final List<Consumer<T>> listeners = new CopyOnWriteArrayList<>();
    
    private final ReentrantLock lock = new ReentrantLock();
    
    private volatile BooleanSupplier activeGuard = () -> true;
    
    private transient java.lang.reflect.Type genericType;
    public java.lang.reflect.Type getGenericType() { return genericType; }
    public void setGenericType(java.lang.reflect.Type genericType) { this.genericType = genericType; }

    public ReactiveVar(T initial) { this.value = initial; }

    public T get() { return value; }

    public void set(T newValue) {
        List<Consumer<T>> snapshot = null;
        
        lock.lock();
        try {
            this.value = newValue;
            
            if (!activeGuard.getAsBoolean()) {
                return;
            }
            
            // Tomamos una instantánea rápida de los listeners y soltamos la memoria
            snapshot = new ArrayList<>(listeners);
        } finally {
            lock.unlock(); // 🔓 Siempre liberar inmediatamente
        }
        
        // 🚀 Disparamos los eventos libres de bloqueos
        if (snapshot != null) {
            snapshot.forEach(l -> l.accept(newValue));
        }
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
}