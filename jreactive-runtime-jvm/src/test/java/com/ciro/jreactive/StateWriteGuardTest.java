package com.ciro.jreactive;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StateWriteGuard - conformidad de tipo en escrituras del navegador")
class StateWriteGuardTest {

    private final StateWriteGuard guard = new StateWriteGuard(new ObjectMapper());

    @Test
    @DisplayName("Convierte el valor entrante al tipo declarado del binding")
    void convierteAlTipoDeclarado() {
        ReactiveVar<Integer> target = new ReactiveVar<>(7);
        target.setGenericType(Integer.class);

        StateWriteGuard.Verdict verdict = guard.inspect(target, "9");

        assertThat(verdict.accepted()).isTrue();
        assertThat(verdict.value()).isEqualTo(9);
    }

    @Test
    @DisplayName("Rechaza un objeto cuando el binding declara un numero")
    void rechazaFormaIncompatible() {
        ReactiveVar<Integer> target = new ReactiveVar<>(7);
        target.setGenericType(Integer.class);

        StateWriteGuard.Verdict verdict = guard.inspect(target, Map.of("objeto", "raro"));

        assertThat(verdict.accepted()).isFalse();
        assertThat(verdict.value()).isNull();
        assertThat(verdict.reason()).isNotBlank();
    }

    @Test
    @DisplayName("Sin tipo declarado cae al tipo del valor vigente")
    void usaElTipoDelValorVigente() {
        ReactiveVar<Integer> target = new ReactiveVar<>(7);

        StateWriteGuard.Verdict verdict = guard.inspect(target, Map.of("objeto", "raro"));

        assertThat(verdict.accepted()).isFalse();
    }

    @Test
    @DisplayName("Sin tipo declarado y sin valor vigente deja pasar la escritura")
    void dejaPasarCuandoNoHayTipoConocido() {
        ReactiveVar<Object> target = new ReactiveVar<>(null);

        StateWriteGuard.Verdict verdict = guard.inspect(target, "cualquier cosa");

        assertThat(verdict.accepted()).isTrue();
        assertThat(verdict.value()).isEqualTo("cualquier cosa");
    }

    @Test
    @DisplayName("Un valor nulo del navegador se acepta tal cual")
    void aceptaNuloDelNavegador() {
        ReactiveVar<Integer> target = new ReactiveVar<>(7);
        target.setGenericType(Integer.class);

        StateWriteGuard.Verdict verdict = guard.inspect(target, null);

        assertThat(verdict.accepted()).isTrue();
        assertThat(verdict.value()).isNull();
    }
}