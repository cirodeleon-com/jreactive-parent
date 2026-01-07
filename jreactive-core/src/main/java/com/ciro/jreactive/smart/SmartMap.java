package com.ciro.jreactive.smart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mapa reactivo Thread-Safe.
 */
public class SmartMap<K, V> extends HashMap<K, V> {

    private final List<Change> changes = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean dirty = false;

    public SmartMap() { super(); }
    public SmartMap(Map<? extends K, ? extends V> m) { super(m); }

    public record Change(String op, Object key, Object value) {}

    @Override
    public synchronized V put(K key, V value) {
        V old = super.put(key, value);
        changes.add(new Change("PUT", key, value));
        dirty = true;
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
        if (containsKey(key)) {
            changes.add(new Change("REMOVE", key, null));
            dirty = true;
            return super.remove(key);
        }
        return null;
    }

    @Override
    public synchronized void clear() {
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
    
    public void clearChanges() {
        this.changes.clear();
        this.dirty = false;
    }
    
    public synchronized void update(K key) {
        if (this.containsKey(key)) {
            this.put(key, this.get(key));
        }
    }
    
    public synchronized List<Change> drainChanges() {
        if (!dirty || changes.isEmpty()) {
            return Collections.emptyList();
        }
        List<Change> snapshot = new ArrayList<>(this.changes);
        this.changes.clear();
        this.dirty = false;
        return snapshot;
    }
}