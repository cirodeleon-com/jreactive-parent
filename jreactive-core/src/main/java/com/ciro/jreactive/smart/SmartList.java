package com.ciro.jreactive.smart;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Lista reactiva Thread-Safe.
 * Protege contra modificaciones concurrentes (Timer vs WebSocket).
 */
public class SmartList<E> extends ArrayList<E> {

    // ✅ FIX: Lista sincronizada para evitar ConcurrentModificationException al leer cambios
    private final List<Change> changes = Collections.synchronizedList(new ArrayList<>());
    
    // ✅ FIX: Volatile asegura visibilidad inmediata entre hilos
    private volatile boolean dirty = false;

    public SmartList() { super(); }
    public SmartList(Collection<? extends E> c) { super(c); }

    public record Change(String op, int index, Object item) {}

    // --- Métodos Interceptados (Sincronizados) ---

    @Override
    public synchronized boolean add(E e) {
        changes.add(new Change("ADD", this.size(), e));
        dirty = true;
        return super.add(e);
    }

    @Override
    public synchronized void add(int index, E element) {
        changes.add(new Change("ADD", index, element));
        dirty = true;
        super.add(index, element);
    }

    @Override
    public synchronized E remove(int index) {
        changes.add(new Change("REMOVE", index, null));
        dirty = true;
        return super.remove(index);
    }

    @Override
    public synchronized boolean remove(Object o) {
        int index = this.indexOf(o);
        if (index >= 0) {
            changes.add(new Change("REMOVE", index, null));
            dirty = true;
            super.remove(index); 
            return true;
        }
        return false;
    }

    @Override
    public synchronized void clear() {
        changes.add(new Change("CLEAR", 0, null));
        dirty = true;
        super.clear();
    }

    @Override
    public synchronized E set(int index, E element) {
        changes.add(new Change("SET", index, element));
        dirty = true;
        return super.set(index, element);
    }

    /** Helper para notificar cambios internos en objetos sin reemplazarlos */
    public synchronized void update(int index) {
        if (index >= 0 && index < this.size()) {
            this.set(index, this.get(index));
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
}