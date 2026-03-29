package com.ciro.jreactive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventBusTest {

    @Test
    @DisplayName("Debe registrar un listener y recibir el payload al emitir un evento")
    void testEmitAndReceive() {
        EventBus bus = new EventBus();
        List<String> mensajesRecibidos = new ArrayList<>();

        // Suscribimos el listener
        bus.on("chat.nuevo_mensaje", (String msg) -> mensajesRecibidos.add(msg));

        // Emitimos
        bus.emit("chat.nuevo_mensaje", "Hola compa");
        bus.emit("chat.nuevo_mensaje", "Todo verde");

        assertThat(mensajesRecibidos).containsExactly("Hola compa", "Todo verde");
    }

    @Test
    @DisplayName("No debe fallar si se emite un evento sin listeners")
    void testEmitWithoutListeners() {
        EventBus bus = new EventBus();
        
        // Esto no debe lanzar NullPointerException ni nada parecido
        bus.emit("evento.fantasma", "Nadie escucha");
        
        // Si llegamos aquí sin errores, el test pasa
        assertThat(true).isTrue();
    }
}