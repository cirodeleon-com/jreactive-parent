package com.ciro.jreactive.smart;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SmartList<E> extends ArrayList<E> {

    // Bitácora de operaciones (Deltas)
    private final List<Change> changes = new ArrayList<>();
    
    // Flag simple para saber si hay algo que sincronizar
    private boolean dirty = false;

    public SmartList() { super(); }
    public SmartList(Collection<? extends E> c) { super(c); }

    // --- Estructura del Cambio ---
    public record Change(String op, int index, Object item) {}

    // --- Métodos Interceptados ---

    @Override
    public boolean add(E e) {
        // 1. Registrar QUÉ pasó (Delta)
        changes.add(new Change("ADD", this.size(), e));
        dirty = true;
        // 2. Ejecutar
        return super.add(e);
    }

    @Override
    public void add(int index, E element) {
        changes.add(new Change("ADD", index, element));
        dirty = true;
        super.add(index, element);
    }

    @Override
    public E remove(int index) {
        changes.add(new Change("REMOVE", index, null));
        dirty = true;
        return super.remove(index);
    }

    @Override
    public boolean remove(Object o) {
        // 1. Primero buscamos en qué posición está el objeto
        int index = this.indexOf(o);

        // 2. Si el índice es >= 0, significa que existe
        if (index >= 0) {
            // A) Registramos el cambio con el ÍNDICE EXACTO (Vital para el frontend)
            changes.add(new Change("REMOVE", index, null));
            dirty = true;

            // B) Llamamos a remove(int) usando el índice que acabamos de encontrar.
            // Esto evita la ambigüedad y el error de casting.
            super.remove(index); 

            return true; // Devolvemos true porque sí se borró
        }

        return false; // No estaba en la lista, no hacemos nada
    }

    @Override
    public void clear() {
        changes.add(new Change("CLEAR", 0, null));
        dirty = true;
        super.clear();
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
