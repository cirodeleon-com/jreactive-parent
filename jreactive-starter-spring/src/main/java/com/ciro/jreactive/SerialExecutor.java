package com.ciro.jreactive;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public final class SerialExecutor {
    private final Executor executor;
    private final AtomicReference<CompletableFuture<Void>> tail = new AtomicReference<>(CompletableFuture.completedFuture(null));

    public SerialExecutor(Executor executor) {
        this.executor = executor;
    }

    public <T> CompletableFuture<T> submit(Callable<T> task) {
        CompletableFuture<T> out = new CompletableFuture<>();

        tail.getAndUpdate(prev ->
            prev.handleAsync((ok, err) -> null, executor)
                .thenComposeAsync(_x -> {
                    try {
                        T val = task.call();
                        out.complete(val);
                    } catch (Throwable t) {
                        out.completeExceptionally(t);
                    }
                    return CompletableFuture.completedFuture(null);
                }, executor)
        );

        return out;
    }
}
