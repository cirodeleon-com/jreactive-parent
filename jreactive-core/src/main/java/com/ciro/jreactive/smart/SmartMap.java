package com.ciro.jreactive.smart;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class SmartMap<K, V> extends HashMap<K, V> {

    private final transient List<Consumer<Change>> listeners = new CopyOnWriteArrayList<>();

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
        V old = super.put(key, value);
        fire("PUT", key, value);
        return old;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    @Override
    public V remove(Object key) {
        boolean removed = false;
        V value = null;
        if (containsKey(key)) {
            value = super.remove(key);
            removed = true;
        }
        if (removed) {
            fire("REMOVE", key, null);
        }
        return value;
    }

    @Override
    public void clear() {
        boolean wasEmpty = this.isEmpty();
        if (!wasEmpty) {
            super.clear();
            fire("CLEAR", null, null);
        }
    }

    public void update(K key) {
        boolean exists = this.containsKey(key);
        V value = null;
        if (exists) {
            value = this.get(key);
            super.put(key, value); 
        }
        
        if (exists) {
            fire("PUT", key, value);
        }
    }
}