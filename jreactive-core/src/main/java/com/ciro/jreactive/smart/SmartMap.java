package com.ciro.jreactive.smart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SmartMap<K, V> extends HashMap<K, V> {

    private final List<Change> changes = new ArrayList<>();
    private boolean dirty = false;

    public SmartMap() { super(); }
    public SmartMap(Map<? extends K, ? extends V> m) { super(m); }

    // Delta: Qué clave cambió y cuál es su nuevo valor (si aplica)
    public record Change(String op, Object key, Object value) {}

    @Override
    public V put(K key, V value) {
        // Podríamos optimizar verificando si el valor nuevo es equals al anterior
        // pero por ahora asumimos que un PUT siempre es una intención de cambio.
        V old = super.put(key, value);
        
        changes.add(new Change("PUT", key, value));
        dirty = true;
        
        return old;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
            put(e.getKey(), e.getValue()); // Reutilizamos put para registrar cada cambio
        }
    }

    @Override
    public V remove(Object key) {
        if (containsKey(key)) {
            changes.add(new Change("REMOVE", key, null));
            dirty = true;
            return super.remove(key);
        }
        return null;
    }

    @Override
    public void clear() {
        if (!isEmpty()) {
            changes.add(new Change("CLEAR", null, null));
            dirty = true;
            super.clear();
        }
    }

    // --- API para el Framework ---

    public boolean isDirty() {
        return dirty;
    }

    public List<Change> getChanges() {
        return changes;
    }

    public void clearDirty() {
        dirty = false;
        changes.clear();
    }
}