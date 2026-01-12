package com.ciro.jreactive.smart;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * Set reactivo Thread-Safe.
 */
public class SmartSet<E> extends HashSet<E> {

    private final List<Change> changes = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean dirty = false;

    // ✅ Opción A: callback cuando hay cambios in-place (deltas)
    private transient Runnable onDirty;

    public SmartSet() { super(); }
    public SmartSet(Collection<? extends E> c) { super(c); }

    public record Change(String op, Object item) {}

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
    public synchronized boolean add(E e) {
        if (super.add(e)) {
            markDirty(new Change("ADD", e));
            return true;
        }
        return false;
    }

    @Override
    public synchronized boolean remove(Object o) {
        if (super.remove(o)) {
            markDirty(new Change("REMOVE", o));
            return true;
        }
        return false;
    }

    @Override
    public synchronized void clear() {
        if (!this.isEmpty()) {
            markDirty(new Change("CLEAR", null));
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

    /**
     * Fuerza la actualización de un elemento (Remove + Add).
     */
    public synchronized void update(E element) {
        if (this.contains(element)) {
            markDirty(new Change("REMOVE", element));
            markDirty(new Change("ADD", element));
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
