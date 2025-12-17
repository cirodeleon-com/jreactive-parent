package com.ciro.jreactive.smart;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class SmartSet<E> extends HashSet<E> {

    private final List<Change> changes = new ArrayList<>();
    private boolean dirty = false;

    public SmartSet() { super(); }
    public SmartSet(Collection<? extends E> c) { super(c); }

    // En un Set, el cambio es solo el objeto, sin posición
    public record Change(String op, Object item) {}

    @Override
    public boolean add(E e) {
        // Solo registramos si realmente se agregó (no existía)
        if (super.add(e)) {
            changes.add(new Change("ADD", e));
            dirty = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean remove(Object o) {
        // Solo registramos si realmente se borró (existía)
        if (super.remove(o)) {
            changes.add(new Change("REMOVE", o));
            dirty = true;
            return true;
        }
        return false;
    }

    @Override
    public void clear() {
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
}
