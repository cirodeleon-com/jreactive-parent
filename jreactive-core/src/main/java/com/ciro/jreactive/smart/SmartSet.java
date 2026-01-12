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
        if (super.add(e)) {
            fire("ADD", e);
            return true;
        }
        return false;
    }

    @Override
    public synchronized boolean remove(Object o) {
        if (super.remove(o)) {
            fire("REMOVE", o);
            return true;
        }
        return false;
    }

    @Override
    public synchronized void clear() {
        if (!this.isEmpty()) {
            fire("CLEAR", null);
            super.clear();
        }
    }

    public synchronized void update(E element) {
        if (this.contains(element)) {
            fire("REMOVE", element);
            fire("ADD", element);
        }
    }
}