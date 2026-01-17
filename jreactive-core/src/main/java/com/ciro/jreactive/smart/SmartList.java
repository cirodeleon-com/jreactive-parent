package com.ciro.jreactive.smart;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate; // 🔥 Necesario para removeIf

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
        int index = this.size();
        boolean result = super.add(e);
        if (result) fire("ADD", index, e);
        return result;
    }

    @Override
    public synchronized void add(int index, E element) {
        super.add(index, element);
        fire("ADD", index, element);
    }

    @Override
    public synchronized E remove(int index) {
        E removed = super.remove(index);
        fire("REMOVE", index, null); // 🔔 Este es el que avisa al JS
        return removed;
    }

    @Override
    public synchronized boolean remove(Object o) {
        int index = this.indexOf(o);
        if (index >= 0) {
            this.remove(index); // Llamamos a remove(int) para centralizar el fire
            return true;
        }
        return false;
    }

    @Override
    public synchronized E set(int index, E element) {
        E old = super.set(index, element);
        fire("SET", index, element);
        return old;
    }

    @Override
    public synchronized void clear() {
        if (!isEmpty()) {
            super.clear();
            fire("CLEAR", 0, null);
        }
    }

    // --- 🔥 LA CIRUGÍA: removeIf Reactivo ---

    @Override
    public synchronized boolean removeIf(Predicate<? super E> filter) {
        boolean modified = false;
        // Iteramos hacia atrás para que los índices no se muevan mientras borramos
        for (int i = size() - 1; i >= 0; i--) {
            if (filter.test(get(i))) {
                this.remove(i); // 🔥 Al llamar a this.remove(i), se dispara el fire("REMOVE")
                modified = true;
            }
        }
        return modified;
    }

    // --- Operaciones Masivas (Ya las tenías bien, pero aseguramos integridad) ---

    @Override
    public synchronized boolean removeAll(Collection<?> c) {
        boolean modified = false;
        for (Object x : new ArrayList<>(this)) { 
            if (c.contains(x)) {
                if (this.remove(x)) modified = true;
            }
        }
        return modified;
    }

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