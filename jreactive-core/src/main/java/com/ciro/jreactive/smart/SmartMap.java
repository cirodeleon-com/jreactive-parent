package com.ciro.jreactive.smart;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock; // 👈 Import nuevo
import java.util.function.Consumer;

public class SmartMap<K, V> extends HashMap<K, V> {

    private final transient List<Consumer<Change>> listeners = new CopyOnWriteArrayList<>();
    private final transient ReentrantLock lock = new ReentrantLock(); // 🔒

    public SmartMap() { super(); }
    public SmartMap(Map<? extends K, ? extends V> m) { super(m); }

    public record Change(String op, Object key, Object value) {}

    public void subscribe(Consumer<Change> listener) {
        listeners.add(listener);
    }

    public void unsubscribe(Consumer<Change> listener) {
        listeners.remove(listener);
    }

    private void fire(String op, Object key, Object value) {
        if (listeners.isEmpty()) return;
        Change c = new Change(op, key, value);
        for (Consumer<Change> l : listeners) {
            try { l.accept(c); } catch (Exception e) { e.printStackTrace(); }
        }
    }

    @Override
    public V put(K key, V value) {
        lock.lock();
        try {
            V old = super.put(key, value);
            fire("PUT", key, value);
            return old;
        } finally {
            lock.unlock();
        }
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
        lock.lock();
        try {
            if (containsKey(key)) {
                V value = super.remove(key);
                fire("REMOVE", key, null);
                return value;
            }
            return null;
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
                fire("CLEAR", null, null);
            }
        } finally {
            lock.unlock();
        }
    }

    public void update(K key) {
        lock.lock();
        try {
            if (this.containsKey(key)) {
                this.put(key, this.get(key));
            }
        } finally {
            lock.unlock();
        }
    }
}