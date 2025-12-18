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

    public SmartSet() { super(); }
    public SmartSet(Collection<? extends E> c) { super(c); }

    public record Change(String op, Object item) {}

    @Override
    public synchronized boolean add(E e) {
        if (super.add(e)) {
            changes.add(new Change("ADD", e));
            dirty = true;
            return true;
        }
        return false;
    }

    @Override
    public synchronized boolean remove(Object o) {
        if (super.remove(o)) {
            changes.add(new Change("REMOVE", o));
            dirty = true;
            return true;
        }
        return false;
    }

    @Override
    public synchronized void clear() {
        if (!this.isEmpty()) {
            changes.add(new Change("CLEAR", null));
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
    
    /**
     * Fuerza la actualización de un elemento (Remove + Add).
     */
    public synchronized void update(E element) {
        if (this.contains(element)) {
            changes.add(new Change("REMOVE", element));
            changes.add(new Change("ADD", element));
            dirty = true;
        }
    }
}