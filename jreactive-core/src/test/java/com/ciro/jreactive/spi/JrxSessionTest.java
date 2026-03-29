package com.ciro.jreactive.spi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JrxSession Unit Tests")
class JrxSessionTest {

    // Implementación anónima para testear el contrato de la interfaz
    static class MockSession implements JrxSession {
        private final Map<String, Object> attrs = new HashMap<>();
        public String lastSent;

        @Override public String getId() { return "test-session"; }
        @Override public boolean isOpen() { return true; }
        @Override public void sendText(String json) { this.lastSent = json; }
        @Override public void close() {}
        @Override public void setAttr(String k, Object v) { attrs.put(k, v); }
        @Override public Object getAttr(String k) { return attrs.get(k); }
    }

    @Test
    @DisplayName("Debe manejar atributos y envío de texto correctamente")
    void testSessionContract() {
        MockSession session = new MockSession();
        
        session.setAttr("rol", "admin");
        session.sendText("{\"ping\": true}");

        assertThat(session.getAttr("rol")).isEqualTo("admin");
        assertThat(session.lastSent).contains("ping");
        assertThat(session.isOpen()).isTrue();
    }
}