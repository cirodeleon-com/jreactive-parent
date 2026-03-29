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
}