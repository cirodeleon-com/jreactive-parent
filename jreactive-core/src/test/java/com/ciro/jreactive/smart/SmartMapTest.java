package com.ciro.jreactive.smart;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SmartMapTest {

    @Test
    @DisplayName("Debe emitir eventos precisos al mutar el mapa (PUT, REMOVE, CLEAR)")
    void testMapMutations() {
        SmartMap<String, String> map = new SmartMap<>();
        List<SmartMap.Change> eventos = new ArrayList<>();
        
        // Suscribimos el espía
        map.subscribe(eventos::add);

        // Act: Hacemos mutaciones
        map.put("sesion1", "Ciro");
        map.remove("sesion1");
        map.put("sesion2", "Admin");
        map.clear();

        // Assert: Verificamos que el framework se enteró de todo
        assertThat(eventos).hasSize(4);
        
        assertThat(eventos.get(0).op()).isEqualTo("PUT");
        assertThat(eventos.get(0).key()).isEqualTo("sesion1");
        
        assertThat(eventos.get(1).op()).isEqualTo("REMOVE");
        
        assertThat(eventos.get(3).op()).isEqualTo("CLEAR");
        assertThat(map).isEmpty();
    }

    @Test
    @DisplayName("No debe emitir eventos si está muteado")
    void testMutedMap() {
        SmartMap<String, Integer> map = new SmartMap<>();
        List<SmartMap.Change> eventos = new ArrayList<>();
        map.subscribe(eventos::add);

        map.mute(); // 🤫 Modo silencioso activado
        map.put("A", 1);
        map.remove("A");

        assertThat(eventos).isEmpty(); // Nadie se enteró
        assertThat(map).isEmpty();
    }
    
    @Test
    @DisplayName("Debe emitir eventos PUT individuales al usar putAll")
    void testPutAll() {
        SmartMap<String, Integer> map = new SmartMap<>();
        List<SmartMap.Change> eventos = new ArrayList<>();
        map.subscribe(eventos::add);

        map.putAll(Map.of("A", 1, "B", 2));

        assertThat(map).hasSize(2);
        assertThat(eventos).filteredOn(e -> e.op().equals("PUT")).hasSize(2);
    }

    @Test
    @DisplayName("Debe emitir evento PUT al usar update() en una clave existente")
    void testUpdateKey() {
        SmartMap<String, String> map = new SmartMap<>();
        map.put("tema", "oscuro");
        List<SmartMap.Change> eventos = new ArrayList<>();
        map.subscribe(eventos::add);

        map.update("tema"); // Forzamos actualización

        assertThat(eventos).hasSize(1);
        assertThat(eventos.get(0).op()).isEqualTo("PUT");
        assertThat(eventos.get(0).value()).isEqualTo("oscuro");
    }

    @Test
    @DisplayName("Debe permitir desuscribirse de los eventos")
    void testUnsubscribe() {
        SmartMap<String, String> map = new SmartMap<>();
        List<SmartMap.Change> eventos = new ArrayList<>();
        java.util.function.Consumer<SmartMap.Change> listener = eventos::add;
        
        map.subscribe(listener);
        map.unsubscribe(listener);
        map.put("X", "Y");

        assertThat(eventos).isEmpty();
    }
}