package com.ciro.jreactive.smart;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class SmartSetTest {

    @Test
    @DisplayName("Debe emitir eventos precisos al mutar el set (ADD, REMOVE, CLEAR)")
    void testSetMutations() {
        SmartSet<String> set = new SmartSet<>();
        List<SmartSet.Change> eventos = new ArrayList<>();
        
        set.subscribe(eventos::add);

        // Act
        set.add("Elemento 1");
        set.remove("Elemento 1");
        set.add("Elemento 2");
        set.clear();

        // Assert
        assertThat(eventos).hasSize(4);
        
        assertThat(eventos.get(0).op()).isEqualTo("ADD");
        assertThat(eventos.get(0).item()).isEqualTo("Elemento 1");
        
        assertThat(eventos.get(1).op()).isEqualTo("REMOVE");
        
        assertThat(eventos.get(3).op()).isEqualTo("CLEAR");
        assertThat(set).isEmpty();
    }

    @Test
    @DisplayName("Debe manejar el método update() emitiendo REMOVE y ADD")
    void testUpdateMethod() {
        SmartSet<String> set = new SmartSet<>();
        List<SmartSet.Change> eventos = new ArrayList<>();
        
        set.add("Fijo");
        set.subscribe(eventos::add); // Suscribimos DESPUÉS de agregar

        // Act: Actualizamos un elemento existente
        set.update("Fijo");

        // Assert: Un update() interno equivale a quitarlo y ponerlo para forzar el render
        assertThat(eventos).hasSize(2);
        assertThat(eventos.get(0).op()).isEqualTo("REMOVE");
        assertThat(eventos.get(1).op()).isEqualTo("ADD");
    }
    
    @Test
    @DisplayName("Debe emitir REMOVE y ADD al ejecutar update()")
    void testUpdateElement() {
        SmartSet<String> set = new SmartSet<>();
        set.add("Ciro");
        List<SmartSet.Change> eventos = new ArrayList<>();
        set.subscribe(eventos::add);

        set.update("Ciro");

        // El update del Set quita y pone para asegurar que el motor detecte el cambio
        assertThat(eventos).hasSize(2);
        assertThat(eventos.get(0).op()).isEqualTo("REMOVE");
        assertThat(eventos.get(1).op()).isEqualTo("ADD");
    }

    @Test
    @DisplayName("No debe emitir eventos si está muteado")
    void testMuteSet() {
        SmartSet<Integer> set = new SmartSet<>();
        List<SmartSet.Change> eventos = new ArrayList<>();
        set.subscribe(eventos::add);

        set.mute();
        set.add(100);
        set.clear();

        assertThat(eventos).isEmpty();
        assertThat(set).isEmpty();
    }
}