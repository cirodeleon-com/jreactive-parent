package com.ciro.jreactive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TypeTest {

    @Test
    @DisplayName("Debe inicializar correctamente usando of()")
    void testInitWithOf() {
        Type<Integer> edad = Type.of(30);
        assertThat(edad.get()).isEqualTo(30);
    }

    @Test
    @DisplayName("Debe inicializar correctamente usando el alias $()")
    void testInitWithAlias() {
        Type<String> framework = Type.$("JReactive");
        assertThat(framework.get()).isEqualTo("JReactive");
    }

    @Test
    @DisplayName("Debe permitir mutar el valor interno con set()")
    void testSetAndGet() {
        Type<Boolean> activo = Type.$(false);
        activo.set(true);
        
        assertThat(activo.get()).isTrue();
    }
}