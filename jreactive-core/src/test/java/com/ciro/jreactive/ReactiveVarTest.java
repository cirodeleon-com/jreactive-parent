package com.ciro.jreactive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReactiveVarTest {

    @Test
    @DisplayName("Debe almacenar y devolver el valor inicial")
    void testInitialValue() {
        ReactiveVar<String> nombre = new ReactiveVar<>("Ciro");
        assertThat(nombre.get()).isEqualTo("Ciro");
    }

    @Test
    @DisplayName("Debe notificar a los listeners cuando el valor cambia con set()")
    void testOnChangeNotification() {
        ReactiveVar<Integer> contador = new ReactiveVar<>(0);
        List<Integer> capturas = new ArrayList<>();

        // Suscribimos el listener (Simula el AOT o el envío del WebSocket)
        contador.onChange(capturas::add); 

        contador.set(1);
        contador.set(2);

        assertThat(contador.get()).isEqualTo(2);
        assertThat(capturas).containsExactly(1, 2);
    }

    @Test
    @DisplayName("Debe actualizar el valor silenciosamente sin disparar eventos")
    void testSetSilent() {
        ReactiveVar<String> estado = new ReactiveVar<>("Inactivo");
        List<String> capturas = new ArrayList<>();

        estado.onChange(capturas::add);

        estado.setSilent("Activo"); // 🤫 Modo ninja

        assertThat(estado.get()).isEqualTo("Activo");
        assertThat(capturas).isEmpty(); // No debió dispararse nada
    }

    @Test
    @DisplayName("No debe notificar a los listeners si el Active Guard es falso (UNMOUNTED)")
    void testActiveGuard() {
        ReactiveVar<Boolean> flag = new ReactiveVar<>(false);
        List<Boolean> capturas = new ArrayList<>();

        flag.onChange(capturas::add);
        
        // Simulamos que el componente se desmontó de la vista
        flag.setActiveGuard(() -> false); 

        flag.set(true);

        assertThat(flag.get()).isTrue();
        assertThat(capturas).isEmpty(); // El guard bloqueó la emisión del evento
    }

    @Test
    @DisplayName("Debe limpiar los listeners correctamente para evitar memory leaks")
    void testClearListeners() {
        ReactiveVar<String> texto = new ReactiveVar<>("A");
        List<String> capturas = new ArrayList<>();

        texto.onChange(capturas::add);
        texto.clearListeners(); // 🧹 Limpieza

        texto.set("B");

        assertThat(texto.get()).isEqualTo("B");
        assertThat(capturas).isEmpty();
    }
    
    @Test
    @DisplayName("Debe permitir setear y recuperar el tópico compartido (Pub/Sub)")
    void testSharedTopic() {
        ReactiveVar<String> rx = new ReactiveVar<>("valor");
        
        assertThat(rx.getSharedTopic()).isNull();
        
        rx.setSharedTopic("sala-123");
        assertThat(rx.getSharedTopic()).isEqualTo("sala-123");
    }

    @Test
    @DisplayName("Debe permitir recuperar el tipo genérico para la serialización")
    void testGenericType() {
        ReactiveVar<Integer> rx = new ReactiveVar<>(10);
        java.lang.reflect.Type tipoInt = Integer.class;
        
        rx.setGenericType(tipoInt);
        assertThat(rx.getGenericType()).isEqualTo(tipoInt);
    }
    
    @Test
    @DisplayName("Debe permitir cambiar el ActiveGuard dinámicamente")
    void testChangeActiveGuard() {
        ReactiveVar<String> rx = new ReactiveVar<>("inicial");
        java.util.concurrent.atomic.AtomicInteger disparos = new java.util.concurrent.atomic.AtomicInteger(0);
        rx.onChange(v -> disparos.incrementAndGet());

        // Guard que bloquea
        rx.setActiveGuard(() -> false);
        rx.set("bloqueado");
        assertThat(disparos.get()).isEqualTo(0);

        // Volvemos a activar
        rx.setActiveGuard(null); // Debería resetear a true por defecto en tu código
        rx.set("activo");
        assertThat(disparos.get()).isEqualTo(1);
    }
}