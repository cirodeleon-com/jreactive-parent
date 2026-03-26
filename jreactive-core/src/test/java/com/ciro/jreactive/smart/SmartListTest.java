package com.ciro.jreactive.smart;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SmartListTest {

    @Test
    @DisplayName("Debe emitir un evento ADD con el índice y elemento correctos al insertar")
    void shouldEmitAddEvent() {
        // Arrange
        SmartList<String> list = new SmartList<>();
        List<SmartList.Change> events = new ArrayList<>();
        
        // Suscribimos un espía para capturar el evento que JReactive enviaría por WebSocket
        list.subscribe(events::add);
        
        // Act
        list.add("Ciro");
        
        // Assert
        assertThat(events).hasSize(1);
        assertThat(events.get(0).op()).isEqualTo("ADD");
        assertThat(events.get(0).index()).isEqualTo(0);
        assertThat(events.get(0).item()).isEqualTo("Ciro");
    }

    @Test
    @DisplayName("Debe emitir un evento REMOVE correcto al eliminar un elemento")
    void shouldEmitRemoveEvent() {
        // Arrange
        SmartList<String> list = new SmartList<>(List.of("A", "B", "C"));
        List<SmartList.Change> events = new ArrayList<>();
        list.subscribe(events::add);
        
        // Act
        list.remove(1); // Elimina "B"
        
        // Assert
        assertThat(events).hasSize(1);
        assertThat(events.get(0).op()).isEqualTo("REMOVE");
        assertThat(events.get(0).index()).isEqualTo(1);
        assertThat(events.get(0).item()).isNull(); // Tu implementación actual envía null en remove
        assertThat(list).containsExactly("A", "C"); // Verifica que la lista interna sí mutó
    }
    
    @Test
    @DisplayName("Debe emitir un evento SET al actualizar un elemento existente")
    void shouldEmitSetEvent() {
        // Arrange
        SmartList<String> list = new SmartList<>(List.of("A", "B"));
        List<SmartList.Change> events = new ArrayList<>();
        list.subscribe(events::add);
        
        // Act
        list.set(0, "Z");
        
        // Assert
        assertThat(events).hasSize(1);
        assertThat(events.get(0).op()).isEqualTo("SET");
        assertThat(events.get(0).index()).isEqualTo(0);
        assertThat(events.get(0).item()).isEqualTo("Z");
    }
}