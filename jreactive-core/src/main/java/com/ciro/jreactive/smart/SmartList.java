package com.ciro.jreactive.smart;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class SmartList<E> extends ArrayList<E> {

    // CopyOnWriteArrayList protege la lista de listeners sin necesidad de locks
    private final transient List<Consumer<Change>> listeners = new CopyOnWriteArrayList<>();

    public SmartList() { super(); }
    public SmartList(Collection<? extends E> c) { super(c); }

    public record Change(String op, int index, Object item) {}

    public void subscribe(Consumer<Change> listener) { listeners.add(listener); }
    public void unsubscribe(Consumer<Change> listener) { listeners.remove(listener); }

    private void fire(String op, int index, Object item) {
        if (listeners.isEmpty()) return;
        Change c = new Change(op, index, item);
        for (Consumer<Change> l : listeners) {
            try { l.accept(c); } catch (Exception e) { e.printStackTrace(); }
        }
    }

    @Override
    public boolean add(E e) {
        int index = this.size();
        boolean result = super.add(e);
        if (result) fire("ADD", index, e);
        return result;
    }

    @Override
    public void add(int index, E element) {
        super.add(index, element);
        fire("ADD", index, element);
    }

    @Override
    public E remove(int index) {
        E removed = super.remove(index);
        fire("REMOVE", index, null);
        return removed;
    }

    @Override
    public boolean remove(Object o) {
        int index = this.indexOf(o);
        if (index >= 0) {
            super.remove(index); 
            fire("REMOVE", index, null);
            return true;
        }
        return false;
    }

    @Override
    public E set(int index, E element) {
        E old = super.set(index, element);
        fire("SET", index, element);
        return old;
    }

    @Override
    public void clear() {
        boolean wasEmpty = this.isEmpty();
        if (!wasEmpty) {
            super.clear();
            fire("CLEAR", 0, null);
        }
    }

    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        boolean modified = false;
        // Iterar en reversa para evitar problemas de índices al remover
        for (int i = size() - 1; i >= 0; i--) {
            if (filter.test(get(i))) {
                this.remove(i); 
                modified = true;
            }
        }
        return modified;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean modified = false;
        // Creamos copia temporal para evitar ConcurrentModificationException si 'c' es esta misma lista
        for (Object x : new ArrayList<>(this)) { 
            if (c.contains(x)) {
                if (this.remove(x)) modified = true;
            }
        }
        return modified;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        boolean modified = false;
        for (Object x : new ArrayList<>(this)) {
            if (!c.contains(x)) {
                if (this.remove(x)) modified = true;
            }
        }
        return modified;
    }
    
    @Override
    public boolean addAll(Collection<? extends E> c) {
        boolean modified = false;
        for (E e : c) {
            if (add(e)) modified = true;
        }
        return modified;
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        boolean modified = false;
        int i = index;
        for (E e : c) {
            add(i++, e);
            modified = true;
        }
        return modified;
    }
}