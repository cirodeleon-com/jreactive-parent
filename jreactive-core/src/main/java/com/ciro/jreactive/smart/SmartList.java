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

    // ✅ Lista sincronizada para evitar ConcurrentModificationException al leer cambios
    private final List<Change> changes = Collections.synchronizedList(new ArrayList<>());

    // ✅ Volatile asegura visibilidad inmediata entre hilos
    private volatile boolean dirty = false;

    // ✅ Opción A: callback cuando hay cambios in-place (deltas)
    private transient Runnable onDirty;

    public SmartList() { super(); }
    public SmartList(Collection<? extends E> c) { super(c); }

    public record Change(String op, int index, Object item) {}

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

    // --- Métodos Interceptados (Sincronizados) ---

    @Override
    public synchronized boolean add(E e) {
        markDirty(new Change("ADD", this.size(), e));
        return super.add(e);
    }

    @Override
    public synchronized void add(int index, E element) {
        markDirty(new Change("ADD", index, element));
        super.add(index, element);
    }

    @Override
    public synchronized E remove(int index) {
        markDirty(new Change("REMOVE", index, null));
        return super.remove(index);
    }

    @Override
    public synchronized boolean remove(Object o) {
        int index = this.indexOf(o);
        if (index >= 0) {
            markDirty(new Change("REMOVE", index, null));
            super.remove(index);
            return true;
        }
        return false;
    }

    @Override
    public synchronized void clear() {
        markDirty(new Change("CLEAR", 0, null));
        super.clear();
    }

    @Override
    public synchronized E set(int index, E element) {
        markDirty(new Change("SET", index, element));
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

    /**
     * 🔥 CRÍTICO: Obtiene los cambios y limpia la lista en UNA sola operación atómica.
     * Esto evita que se pierdan eventos si un hilo escribe justo mientras el WS lee.
     */
    public synchronized List<Change> drainChanges() {
        if (!dirty || changes.isEmpty()) {
            return Collections.emptyList();
        }
        // 1. Copia instantánea (Snapshot)
        List<Change> snapshot = new ArrayList<>(this.changes);

        // 2. Limpieza inmediata
        this.changes.clear();
        this.dirty = false;

        // 3. Retorno seguro
        return snapshot;
    }
}
