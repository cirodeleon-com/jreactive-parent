package com.ciro.jreactive;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class ReactiveVar<T> {
    private T value;
    private final List<Consumer<T>> listeners = new CopyOnWriteArrayList<>();

    public ReactiveVar(T initial) { this.value = initial; }

    public T get() { return value; }

    public void set(T newValue) {
        this.value = newValue;
        listeners.forEach(l -> l.accept(newValue));
    }

    public void onChange(Consumer<T> listener) { listeners.add(listener); }
}
