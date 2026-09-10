package com.ciro.jreactive;

import java.util.AbstractSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

final class DeferredTaskGate extends AbstractSet<String> {

    private final Set<String> running = new HashSet<>();
    private final Map<String, Runnable> pending = new HashMap<>();

    static Set<String> create() {
        return new DeferredTaskGate();
    }

    static void request(
            Set<String> tasks,
            String stateKey,
            Runnable reload) {

        if (tasks instanceof DeferredTaskGate gate) {
            gate.request(stateKey, reload);
            return;
        }

        reload.run();
    }

    private void request(String stateKey, Runnable reload) {
        boolean runNow;

        synchronized (this) {
            runNow = !running.contains(stateKey);
            if (!runNow) {
                pending.put(stateKey, reload);
            }
        }

        if (runNow) {
            reload.run();
        }
    }

    @Override
    public boolean add(String stateKey) {
        synchronized (this) {
            return running.add(stateKey);
        }
    }

    @Override
    public boolean remove(Object stateKey) {
        Runnable reload;
        boolean removed;

        synchronized (this) {
            removed = running.remove(stateKey);
            reload = removed && stateKey instanceof String key
                    ? pending.remove(key)
                    : null;
        }

        if (reload != null) {
            reload.run();
        }

        return removed;
    }

    @Override
    public boolean contains(Object stateKey) {
        synchronized (this) {
            return running.contains(stateKey);
        }
    }

    @Override
    public void clear() {
        synchronized (this) {
            running.clear();
            pending.clear();
        }
    }

    @Override
    public Iterator<String> iterator() {
        synchronized (this) {
            return Set.copyOf(running).iterator();
        }
    }

    @Override
    public int size() {
        synchronized (this) {
            return running.size();
        }
    }
}