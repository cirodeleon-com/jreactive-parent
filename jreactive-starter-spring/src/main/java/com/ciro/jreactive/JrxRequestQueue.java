package com.ciro.jreactive;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Serializa ejecución por (sessionId + path) para evitar carreras entre:
 * - /jrx/set (bindings)
 * - /call/... (eventos @Call)
 * - y múltiples /call concurrentes (doble click, etc.)
 */
@Component
public class JrxRequestQueue {

    private final ConcurrentHashMap<String, SerialExecutor> queues = new ConcurrentHashMap<>();
    private final Executor backend = Executors.newCachedThreadPool();

    public <T> T run(String sessionId, String path, Callable<T> task) {
        String key = (sessionId == null ? "null" : sessionId) + "|" + (path == null ? "/" : path);
        SerialExecutor q = queues.computeIfAbsent(key, _k -> new SerialExecutor(backend));
        try {
            return q.submit(task).get(); // orden garantizado
        } catch (ExecutionException e) {
            // desempaca la causa real para que el log sea útil
            Throwable c = e.getCause();
            if (c instanceof RuntimeException re) throw re;
            throw new RuntimeException(c);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    /**
     * Executor serial (FIFO) sobre un backend concurrente.
     * Garantiza 1 tarea a la vez por key.
     */
    static final class SerialExecutor {
        private final Executor backend;
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        private final AtomicBoolean running = new AtomicBoolean(false);

        SerialExecutor(Executor backend) {
            this.backend = backend;
        }

        <T> Future<T> submit(Callable<T> c) {
            CompletableFuture<T> f = new CompletableFuture<>();
            enqueue(() -> {
                try {
                    f.complete(c.call());
                } catch (Throwable t) {
                    f.completeExceptionally(t);
                }
            });
            return f;
        }

        private void enqueue(Runnable r) {
            synchronized (tasks) {
                tasks.add(r);
            }
            schedule();
        }

        private void schedule() {
            if (!running.compareAndSet(false, true)) return;

            backend.execute(() -> {
                try {
                    while (true) {
                        Runnable next;
                        synchronized (tasks) {
                            next = tasks.poll();
                        }
                        if (next == null) return;
                        next.run();
                    }
                } finally {
                    running.set(false);
                    // si entraron tareas mientras salíamos, reprograma
                    synchronized (tasks) {
                        if (!tasks.isEmpty()) schedule();
                    }
                }
            });
        }
    }
}
