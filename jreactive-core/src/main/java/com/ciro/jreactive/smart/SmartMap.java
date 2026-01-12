package com.ciro.jreactive.smart;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class SmartMap<K, V> extends HashMap<K, V> {

    private final transient List<Consumer<Change>> listeners = new CopyOnWriteArrayList<>();

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
    public synchronized V put(K key, V value) {
        V old = super.put(key, value);
        fire("PUT", key, value);
        return old;
    }

    @Override
    public synchronized void putAll(Map<? extends K, ? extends V> m) {
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    @Override
    public synchronized V remove(Object key) {
        // Verificamos si existe antes, para saber si debemos disparar evento
        if (containsKey(key)) {
            // 1. 🔥 PRIMERO: Mutamos el estado real (Borramos y guardamos el valor)
            V value = super.remove(key);
            
            // 2. ✅ LUEGO: Notificamos
            fire("REMOVE", key, null);
            
            return value;
        }
        return null;
    }

    @Override
    public synchronized void clear() {
        if (!isEmpty()) {
            fire("CLEAR", null, null);
            super.clear();
        }
    }

    public synchronized void update(K key) {
        if (this.containsKey(key)) {
            this.put(key, this.get(key));
        }
    }
}