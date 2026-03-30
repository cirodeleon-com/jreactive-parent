package com.ciro.jreactive.store;

import com.ciro.jreactive.HtmlComponent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CaffeineStateStore - Caché L1 en Memoria")
class CaffeineStateStoreTest {

    static class DummyPage extends HtmlComponent {
        @Override protected String template() { return ""; }
    }

    @Test
    @DisplayName("Debe guardar y recuperar componentes por sesión y ruta")
    void testPutAndGet() {
        CaffeineStateStore store = new CaffeineStateStore();
        DummyPage page = new DummyPage();

        store.put("session1", "/home", page);
        
        assertThat(store.get("session1", "/home")).isSameAs(page);
        assertThat(store.get("session1", "/otro")).isNull();
        assertThat(store.get("session2", "/home")).isNull();
    }

    @Test
    @DisplayName("Debe manejar el versionamiento optimista (Replace)")
    void testOptimisticReplace() {
        CaffeineStateStore store = new CaffeineStateStore();
        DummyPage page = new DummyPage();
        page._setVersion(1);

        store.put("s1", "/p", page);

        // Intento exitoso: la versión coincide
        boolean ok = store.replace("s1", "/p", page, 1);
        assertThat(ok).isTrue();
        assertThat(page._getVersion()).isEqualTo(2);

        // Intento fallido: versión desfasada
        boolean fail = store.replace("s1", "/p", page, 5);
        assertThat(fail).isFalse();
    }

    @Test
    @DisplayName("Debe limpiar sesiones completas")
    void testRemoveSession() {
        CaffeineStateStore store = new CaffeineStateStore();
        store.put("s1", "/a", new DummyPage());
        store.put("s1", "/b", new DummyPage());
        store.put("s2", "/a", new DummyPage());

        store.removeSession("s1");

        assertThat(store.get("s1", "/a")).isNull();
        assertThat(store.get("s2", "/a")).isNotNull();
    }
}