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
    
    // 🔒 Usamos ReentrantLock para evitar el "Pinning" de Virtual Threads
    private final transient ReentrantLock lock = new ReentrantLock();

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
    public boolean add(E e) {
        lock.lock();
        try {
            int index = this.size();
            boolean result = super.add(e);
            if (result) fire("ADD", index, e);
            return result;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void add(int index, E element) {
        lock.lock();
        try {
            super.add(index, element);
            fire("ADD", index, element);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public E remove(int index) {
        lock.lock();
        try {
            E removed = super.remove(index);
            fire("REMOVE", index, null); // 🔔 Este es el que avisa al JS
            return removed;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean remove(Object o) {
        lock.lock();
        try {
            int index = this.indexOf(o);
            if (index >= 0) {
                // Llamamos a remove(int) que ya es thread-safe (ReentrantLock permite reentrada)
                this.remove(index); 
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public E set(int index, E element) {
        lock.lock();
        try {
            E old = super.set(index, element);
            fire("SET", index, element);
            return old;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void clear() {
        lock.lock();
        try {
            if (!isEmpty()) {
                super.clear();
                fire("CLEAR", 0, null);
            }
        } finally {
            lock.unlock();
        }
    }

    // --- 🔥 LA CIRUGÍA: removeIf Reactivo ---

    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        lock.lock();
        try {
            boolean modified = false;
            // Iteramos hacia atrás para que los índices no se muevan mientras borramos
            for (int i = size() - 1; i >= 0; i--) {
                if (filter.test(get(i))) {
                    this.remove(i); // 🔥 Al llamar a this.remove(i), se dispara el fire("REMOVE")
                    modified = true;
                }
            }
            return modified;
        } finally {
            lock.unlock();
        }
    }

    // --- Operaciones Masivas ---

    @Override
    public boolean removeAll(Collection<?> c) {
        lock.lock();
        try {
            boolean modified = false;
            // Copia defensiva para iterar sin problemas de concurrencia externa
            for (Object x : new ArrayList<>(this)) { 
                if (c.contains(x)) {
                    if (this.remove(x)) modified = true;
                }
            }
            return modified;
        } finally {
            lock.unlock();
        }
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
        } finally {
            lock.unlock();
        }
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
        } finally {
            lock.unlock();
        }
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
        } finally {
            lock.unlock();
        }
    }
}