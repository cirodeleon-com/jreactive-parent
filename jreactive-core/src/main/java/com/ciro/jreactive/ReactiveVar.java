package com.ciro.jreactive;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.concurrent.locks.ReentrantLock;

public final class ReactiveVar<T> implements java.io.Serializable{
    private T value;
    private final List<Consumer<T>> listeners = new CopyOnWriteArrayList<>();
    
    private final ReentrantLock lock = new ReentrantLock();
    
    private volatile BooleanSupplier activeGuard = () -> true;

    public ReactiveVar(T initial) { this.value = initial; }

    public T get() { return value; }

    public void set(T newValue) {
    	lock.lock();
    	try {
        this.value = newValue;
        
        if (!activeGuard.getAsBoolean()) {
            return;
        }
        
        listeners.forEach(l -> l.accept(newValue));
    	} finally {
            lock.unlock(); // 🔓 Siempre liberar en finally
        }
    }
    
    public void setActiveGuard(BooleanSupplier guard) {
        this.activeGuard = (guard != null) ? guard : () -> true;
    }
    
    public void clearListeners() {
        listeners.clear();
    }

    //public void onChange(Consumer<T> listener) { listeners.add(listener); }
    
    public Runnable onChange(Consumer<T> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }
    
}
