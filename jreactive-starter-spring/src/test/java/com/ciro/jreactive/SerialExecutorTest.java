package com.ciro.jreactive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("SerialExecutor - Ejecución en serie")
class SerialExecutorTest {

    @Test
    @DisplayName("Debe ejecutar un Callable y retornar su valor exitosamente")
    void shouldExecuteCallableSuccessfully() throws Exception {
        SerialExecutor executor = new SerialExecutor(Executors.newSingleThreadExecutor());
        
        CompletableFuture<String> future = executor.submit(() -> "Ejecutado con éxito");
        
        assertThat(future.get()).isEqualTo("Ejecutado con éxito");
    }

    @Test
    @DisplayName("Debe propagar excepciones lanzadas dentro del Callable")
    void shouldCompleteExceptionallyOnFailure() {
        SerialExecutor executor = new SerialExecutor(Executors.newSingleThreadExecutor());
        
        CompletableFuture<String> future = executor.submit(() -> {
            throw new IllegalArgumentException("Error forzado");
        });
        
        ExecutionException exception = assertThrows(ExecutionException.class, future::get);
        assertThat(exception.getCause()).isInstanceOf(IllegalArgumentException.class);
        assertThat(exception.getCause().getMessage()).isEqualTo("Error forzado");
    }
}