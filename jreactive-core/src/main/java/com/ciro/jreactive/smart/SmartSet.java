package com.ciro.jreactive.smart;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class SmartSet<E> extends HashSet<E> {

    private final transient List<Consumer<Change>> listeners = new CopyOnWriteArrayList<>();

    public SmartSet() { super(); }
    public SmartSet(Collection<? extends E> c) { super(c); }

    public record Change(String op, Object item) {}

    public void subscribe(Consumer<Change> listener) {
        listeners.add(listener);
    }

    public void unsubscribe(Consumer<Change> listener) {
        listeners.remove(listener);
    }

    private void fire(String op, Object item) {
        if (listeners.isEmpty()) return;
        Change c = new Change(op, item);
        for (Consumer<Change> l : listeners) {
            try { l.accept(c); } catch (Exception e) { e.printStackTrace(); }
        }
    }

    @Override
    public synchronized boolean add(E e) {
        boolean result = super.add(e);
        if (result) {
            fire("ADD", e);
        }
        return result;
    }

    @Override
    public synchronized boolean remove(Object o) {
        boolean result = super.remove(o);
        if (result) {
            fire("REMOVE", o);
        }
        return result;
    }

    @Override
    public synchronized void clear() {
        if (!this.isEmpty()) {
            super.clear();
            fire("CLEAR", null);
        }
    }

    public synchronized void update(E element) {
        if (this.contains(element)) {
            fire("REMOVE", element);
            fire("ADD", element);
        }
    }
}