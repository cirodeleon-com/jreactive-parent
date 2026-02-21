package com.ciro.jreactive.smart;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class SmartSet<E> extends HashSet<E> {

    private final transient List<Consumer<Change>> listeners = new CopyOnWriteArrayList<>();
    private final transient ReentrantLock lock = new ReentrantLock();

    public SmartSet() { super(); }
    public SmartSet(Collection<? extends E> c) { super(c); }

    public record Change(String op, Object item) {}

    public void subscribe(Consumer<Change> listener) { listeners.add(listener); }
    public void unsubscribe(Consumer<Change> listener) { listeners.remove(listener); }

    private void fire(String op, Object item) {
        if (listeners.isEmpty()) return;
        Change c = new Change(op, item);
        for (Consumer<Change> l : listeners) {
            try { l.accept(c); } catch (Exception e) { e.printStackTrace(); }
        }
    }

    @Override
    public boolean add(E e) {
        boolean result;
        lock.lock();
        try {
            result = super.add(e);
        } finally {
            lock.unlock();
        }
        if (result) {
            fire("ADD", e);
        }
        return result;
    }

    @Override
    public boolean remove(Object o) {
        boolean result;
        lock.lock();
        try {
            result = super.remove(o);
        } finally {
            lock.unlock();
        }
        if (result) {
            fire("REMOVE", o);
        }
        return result;
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
            fire("CLEAR", null);
        }
    }

    public void update(E element) {
        boolean exists;
        lock.lock();
        try {
            exists = this.contains(element);
        } finally {
            lock.unlock();
        }
        if (exists) {
            fire("REMOVE", element);
            fire("ADD", element);
        }
    }
}