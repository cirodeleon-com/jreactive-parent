package com.ciro.jreactive.smart;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmartSetConstructionTest {

    @Test
    @DisplayName("Debe copiar una colección con elementos sin modificarla ni compartirla")
    void shouldCopyPopulatedCollection() {
        List<String> source = new ArrayList<>(List.of("A", "B", "A"));

        SmartSet<String> set = new SmartSet<>(source);

        assertThat(set).containsExactlyInAnyOrder("A", "B");
        assertThat(source).containsExactly("A", "B", "A");
        source.clear();
        assertThat(set).containsExactlyInAnyOrder("A", "B");
    }

    @Test
    @DisplayName("Debe conservar elementos nulos y eliminar duplicados")
    void shouldPreserveNullElementsAndDeduplicate() {
        SmartSet<String> set = new SmartSet<>(Arrays.asList("A", null, "A", null));

        assertThat(set).containsExactlyInAnyOrder("A", null);
    }

    @Test
    @DisplayName("Debe aceptar una colección vacía y permitir inserciones posteriores")
    void shouldAcceptEmptyCollection() {
        SmartSet<String> set = new SmartSet<>(List.of());

        assertThat(set).isEmpty();
        assertThat(set.add("A")).isTrue();
        assertThat(set).containsExactly("A");
    }

    @Test
    @DisplayName("Debe seguir rechazando una colección nula")
    void shouldRejectNullCollection() {
        assertThrows(NullPointerException.class, () -> new SmartSet<String>(null));
    }

    @Test
    @DisplayName("Debe conservar los eventos después de construirse con elementos")
    void shouldPreserveMutationEvents() {
        SmartSet<String> set = new SmartSet<>(List.of("A"));
        List<SmartSet.Change> events = new ArrayList<>();
        set.subscribe(events::add);

        assertThat(set.add("A")).isFalse();
        assertThat(set.remove("absent")).isFalse();
        set.add("B");
        set.remove("A");
        set.update("B");
        set.clear();

        assertThat(events).containsExactly(
                new SmartSet.Change("ADD", "B"),
                new SmartSet.Change("REMOVE", "A"),
                new SmartSet.Change("REMOVE", "B"),
                new SmartSet.Change("ADD", "B"),
                new SmartSet.Change("CLEAR", null));
        assertThat(set).isEmpty();
    }

    @Test
    @DisplayName("Debe conservar silencio, reactivación y desuscripción")
    void shouldPreserveListenerControls() {
        SmartSet<String> set = new SmartSet<>(List.of("A"));
        List<SmartSet.Change> events = new ArrayList<>();
        Consumer<SmartSet.Change> listener = events::add;
        set.subscribe(listener);

        set.mute();
        set.add("B");
        set.remove("A");
        assertThat(events).isEmpty();
        assertThat(set).containsExactly("B");

        set.unmute();
        set.add("C");
        assertThat(events).containsExactly(new SmartSet.Change("ADD", "C"));

        set.unsubscribe(listener);
        set.add("D");
        assertThat(events).containsExactly(new SmartSet.Change("ADD", "C"));
        assertThat(set).containsExactlyInAnyOrder("B", "C", "D");
    }
}