package com.ciro.jreactive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JrxRequestQueue - Control de concurrencia")
class JrxRequestQueueTest {

    private JrxRequestQueue queue;

    @BeforeEach
    void setUp() {
        queue = new JrxRequestQueue();
    }

    @Test
    @DisplayName("Debe ejecutar tareas del mismo (sessionId + path) secuencialmente")
    void shouldExecuteTasksSequentiallyForSameSession() throws InterruptedException {
        List<Integer> executionOrder = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch lock = new CountDownLatch(1);

        // Disparamos una tarea lenta
        Thread t1 = new Thread(() -> {
            queue.run("session-1", "/home", () -> {
                try {
                    lock.await(); // Espera artificial
                    executionOrder.add(1);
                } catch (InterruptedException e) { }
                return null;
            });
        });

        // Disparamos una tarea rápida al mismo usuario y ruta
        Thread t2 = new Thread(() -> {
            queue.run("session-1", "/home", () -> {
                executionOrder.add(2);
                return null;
            });
        });

        t1.start();
        Thread.sleep(50); // Dar tiempo a que t1 pille el lock de la cola
        t2.start();

        // Liberamos el bloqueo de t1
        lock.countDown();

        t1.join();
        t2.join();

        // El orden DEBE ser 1 y luego 2, porque t2 tuvo que esperar a t1 en la cola SerialExecutor
        assertThat(executionOrder).containsExactly(1, 2);
    }
    
    @Test
    @DisplayName("Debe propagar las excepciones lanzadas en las tareas")
    void shouldPropagateExceptions() {
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> {
            queue.run("session-2", "/error", () -> {
                throw new IllegalStateException("Fallo provocado");
            });
        });
    }
}