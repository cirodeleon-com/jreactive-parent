package com.ciro.jreactive.smart;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class SmartList<E> extends ArrayList<E> {

    private final transient List<Consumer<Change>> listeners = new CopyOnWriteArrayList<>();

    public SmartList() { super(); }
    public SmartList(Collection<? extends E> c) { super(c); }

    public record Change(String op, int index, Object item) {}

    public void subscribe(Consumer<Change> listener) {
        listeners.add(listener);
    }

    public void unsubscribe(Consumer<Change> listener) {
        listeners.remove(listener);
    }

    private void fire(String op, int index, Object item) {
        if (listeners.isEmpty()) return;
        Change c = new Change(op, index, item);
        for (Consumer<Change> l : listeners) {
            try { l.accept(c); } catch (Exception e) { e.printStackTrace(); }
        }
    }

    // --- Operaciones Unitarias ---

    @Override
    public synchronized boolean add(E e) {
        fire("ADD", this.size(), e);
        return super.add(e);
    }

    @Override
    public synchronized void add(int index, E element) {
        fire("ADD", index, element);
        super.add(index, element);
    }

    @Override
    public synchronized E remove(int index) {
        fire("REMOVE", index, null);
        return super.remove(index);
    }

    @Override
    public synchronized boolean remove(Object o) {
        int index = this.indexOf(o);
        if (index >= 0) {
            fire("REMOVE", index, null);
            super.remove(index);
            return true;
        }
        return false;
    }

    @Override
    public synchronized E set(int index, E element) {
        fire("SET", index, element);
        return super.set(index, element);
    }

    @Override
    public synchronized void clear() {
        if (!isEmpty()) {
            fire("CLEAR", 0, null);
            super.clear();
        }
    }
    
    public synchronized void update(int index) {
        if (index >= 0 && index < this.size()) {
            this.set(index, this.get(index));
        }
    }

    // --- 🔥 FIX: Soporte para subList y Operaciones Masivas ---

    /**
     * Esto permite que subList(0, 5).clear() funcione y dispare eventos.
     * Iteramos hacia atrás para no afectar los índices mientras borramos.
     */
    @Override
    protected synchronized void removeRange(int fromIndex, int toIndex) {
        // Borramos uno a uno para disparar "REMOVE" individuales al frontend.
        // Es menos eficiente en CPU pero garantiza consistencia visual.
        for (int i = toIndex - 1; i >= fromIndex; i--) {
            this.remove(i); 
        }
    }

    /**
     * Sobreescribimos removeAll para que use nuestro remove(Object) y dispare eventos.
     */
    @Override
    public synchronized boolean removeAll(Collection<?> c) {
        boolean modified = false;
        // Copiamos para evitar ConcurrentModification si c es this
        for (Object x : new ArrayList<>(this)) { 
            if (c.contains(x)) {
                if (this.remove(x)) modified = true;
            }
        }
        return modified;
    }

    /**
     * Sobreescribimos retainAll para que use nuestro remove(Object).
     */
    @Override
    public synchronized boolean retainAll(Collection<?> c) {
        boolean modified = false;
        for (Object x : new ArrayList<>(this)) {
            if (!c.contains(x)) {
                if (this.remove(x)) modified = true;
            }
        }
        return modified;
    }
    
    // addAll usa internamente add(index, elem) o similar, así que suele funcionar,
    // pero si quisieras forzar eventos uno a uno:
    @Override
    public synchronized boolean addAll(Collection<? extends E> c) {
        boolean modified = false;
        for (E e : c) {
            if (add(e)) modified = true;
        }
        return modified;
    }

    @Override
    public synchronized boolean addAll(int index, Collection<? extends E> c) {
        boolean modified = false;
        int i = index;
        for (E e : c) {
            add(i++, e);
            modified = true;
        }
        return modified;
    }
}