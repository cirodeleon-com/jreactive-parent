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

    // ✅ Opción A: callback cuando hay cambios in-place (deltas)
    private transient Runnable onDirty;

    public SmartMap() { super(); }
    public SmartMap(Map<? extends K, ? extends V> m) { super(m); }

    public record Change(String op, Object key, Object value) {}

    /** Registra callback para notificar que hubo cambios in-place */
    public synchronized void onDirty(Runnable r) {
        this.onDirty = r;
    }

    private void fireDirty() {
        Runnable cb;
        synchronized (this) { cb = this.onDirty; }
        if (cb != null) {
            try { cb.run(); } catch (Exception ignored) {}
        }
    }

    private void markDirty(Change c) {
        changes.add(c);
        dirty = true;
        fireDirty();
    }

    @Override
    public synchronized V put(K key, V value) {
        V old = super.put(key, value);
        markDirty(new Change("PUT", key, value));
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
            markDirty(new Change("REMOVE", key, null));
            return super.remove(key);
        }
        return null;
    }

    @Override
    public synchronized void clear() {
        if (!isEmpty()) {
            markDirty(new Change("CLEAR", null, null));
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
