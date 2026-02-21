package com.ciro.jreactive.smart;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class SmartMap<K, V> extends HashMap<K, V> {

    private final transient List<Consumer<Change>> listeners = new CopyOnWriteArrayList<>();
    private final transient ReentrantLock lock = new ReentrantLock();

    public SmartMap() { super(); }
    public SmartMap(Map<? extends K, ? extends V> m) { super(m); }

    public record Change(String op, Object key, Object value) {}

    public void subscribe(Consumer<Change> listener) { listeners.add(listener); }
    public void unsubscribe(Consumer<Change> listener) { listeners.remove(listener); }

    private void fire(String op, Object key, Object value) {
        if (listeners.isEmpty()) return;
        Change c = new Change(op, key, value);
        for (Consumer<Change> l : listeners) {
            try { l.accept(c); } catch (Exception e) { e.printStackTrace(); }
        }
    }

    @Override
    public V put(K key, V value) {
        V old;
        lock.lock();
        try {
            old = super.put(key, value);
        } finally {
            lock.unlock();
        }
        fire("PUT", key, value);
        return old;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        lock.lock();
        try {
            for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
                put(e.getKey(), e.getValue());
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public V remove(Object key) {
        boolean removed = false;
        V value = null;
        lock.lock();
        try {
            if (containsKey(key)) {
                value = super.remove(key);
                removed = true;
            }
        } finally {
            lock.unlock();
        }
        if (removed) {
            fire("REMOVE", key, null);
        }
        return value;
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
            fire("CLEAR", null, null);
        }
    }

    public void update(K key) {
        boolean exists;
        V value = null;
        lock.lock();
        try {
            exists = this.containsKey(key);
            if (exists) {
                value = this.get(key);
                super.put(key, value); 
            }
        } finally {
            lock.unlock();
        }
        
        if (exists) {
            fire("PUT", key, value);
        }
    }
}