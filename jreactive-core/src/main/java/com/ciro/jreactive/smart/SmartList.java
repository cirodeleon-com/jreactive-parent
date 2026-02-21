package com.ciro.jreactive.smart;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class SmartList<E> extends ArrayList<E> {

    private final transient List<Consumer<Change>> listeners = new CopyOnWriteArrayList<>();
    private final transient ReentrantLock lock = new ReentrantLock();

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
        int index;
        boolean result;
        lock.lock();
        try {
            index = this.size();
            result = super.add(e);
        } finally {
            lock.unlock();
        }
        if (result) fire("ADD", index, e);
        return result;
    }

    @Override
    public void add(int index, E element) {
        lock.lock();
        try {
            super.add(index, element);
        } finally {
            lock.unlock();
        }
        fire("ADD", index, element);
    }

    @Override
    public E remove(int index) {
        E removed;
        lock.lock();
        try {
            removed = super.remove(index);
        } finally {
            lock.unlock();
        }
        fire("REMOVE", index, null);
        return removed;
    }

    @Override
    public boolean remove(Object o) {
        int index = -1;
        lock.lock();
        try {
            index = this.indexOf(o);
            if (index >= 0) {
                super.remove(index); // Evitamos la recursión del lock
            }
        } finally {
            lock.unlock();
        }
        if (index >= 0) {
            fire("REMOVE", index, null);
            return true;
        }
        return false;
    }

    @Override
    public E set(int index, E element) {
        E old;
        lock.lock();
        try {
            old = super.set(index, element);
        } finally {
            lock.unlock();
        }
        fire("SET", index, element);
        return old;
    }

    @Override
    public void clear() {
        boolean wasEmpty;
        lock.lock();
        try {
            wasEmpty = this.isEmpty();
            if (!wasEmpty) {
                super.clear();
            }
        } finally {
            lock.unlock();
        }
        if (!wasEmpty) {
            fire("CLEAR", 0, null);
        }
    }

    // --- Operaciones Masivas (Sin cambios profundos por ahora, llaman a métodos con lock propio) ---
    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        lock.lock();
        try {
            boolean modified = false;
            for (int i = size() - 1; i >= 0; i--) {
                if (filter.test(get(i))) {
                    this.remove(i); 
                    modified = true;
                }
            }
            return modified;
        } finally { lock.unlock(); }
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        lock.lock();
        try {
            boolean modified = false;
            for (Object x : new ArrayList<>(this)) { 
                if (c.contains(x)) {
                    if (this.remove(x)) modified = true;
                }
            }
            return modified;
        } finally { lock.unlock(); }
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        lock.lock();
        try {
            boolean modified = false;
            for (Object x : new ArrayList<>(this)) {
                if (!c.contains(x)) {
                    if (this.remove(x)) modified = true;
                }
            }
            return modified;
        } finally { lock.unlock(); }
    }
    
    @Override
    public boolean addAll(Collection<? extends E> c) {
        lock.lock();
        try {
            boolean modified = false;
            for (E e : c) {
                if (add(e)) modified = true;
            }
            return modified;
        } finally { lock.unlock(); }
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        lock.lock();
        try {
            boolean modified = false;
            int i = index;
            for (E e : c) {
                add(i++, e);
                modified = true;
            }
            return modified;
        } finally { lock.unlock(); }
    }
}