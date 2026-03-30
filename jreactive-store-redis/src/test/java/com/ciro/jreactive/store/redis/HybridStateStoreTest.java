package com.ciro.jreactive.store.redis;

import com.ciro.jreactive.HtmlComponent;
import com.ciro.jreactive.annotations.StatefulRam;
import com.ciro.jreactive.store.StateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HybridStateStore - Pruebas de Caché L1 (RAM) y L2 (Redis)")
class HybridStateStoreTest {

    static class DummyStore implements StateStore {
        public final Map<String, HtmlComponent> memory = new HashMap<>();
        public boolean replaceResult = true;

        @Override public HtmlComponent get(String sid, String path) { return memory.get(sid + path); }
        @Override public void put(String sid, String path, HtmlComponent comp) { memory.put(sid + path, comp); }
        @Override public boolean replace(String sid, String path, HtmlComponent comp, long ver) {
            if (replaceResult) memory.put(sid + path, comp);
            return replaceResult;
        }
        @Override public void remove(String sid, String path) { memory.remove(sid + path); }
        @Override public void removeSession(String sid) { memory.clear(); }
    }

    static class NormalComponent extends HtmlComponent {
        @Override protected String template() { return "normal"; }
    }

    @StatefulRam
    static class RamComponent extends HtmlComponent {
        @Override protected String template() { return "ram"; }
    }

    private DummyStore l1Ram;
    private DummyStore l2Redis;
    private HybridStateStore hybridStrong;
    private HybridStateStore hybridEventual;

    @BeforeEach
    void setUp() {
        l1Ram = new DummyStore();
        l2Redis = new DummyStore();
        hybridStrong = new HybridStateStore(l1Ram, l2Redis, true); // STRONG
        hybridEventual = new HybridStateStore(l1Ram, l2Redis, false); // EVENTUAL
    }

    @Test
    @DisplayName("Debe obtener de RAM sin tocar Redis")
    void testGetFromL1() {
        NormalComponent comp = new NormalComponent();
        l1Ram.put("s1", "/p", comp);
        assertThat(hybridStrong.get("s1", "/p")).isSameAs(comp);
        assertThat(l2Redis.memory).isEmpty();
    }

    @Test
    @DisplayName("Read-Repair: Debe copiar de Redis a RAM")
    void testGetFromL2AndReadRepair() {
        NormalComponent comp = new NormalComponent();
        l2Redis.put("s1", "/p", comp);
        assertThat(hybridStrong.get("s1", "/p")).isSameAs(comp);
        assertThat(l1Ram.memory).hasSize(1);
    }

    @Test
    @DisplayName("Debe guardar asíncronamente en L2 (EVENTUAL)")
    void testPutEventual() throws InterruptedException {
        hybridEventual.put("s1", "/p", new NormalComponent());
        assertThat(l1Ram.memory).hasSize(1);
        Thread.sleep(100); // Esperar hilo secundario
        assertThat(l2Redis.memory).hasSize(1);
    }

    @Test
    @DisplayName("No debe tocar Redis si es @StatefulRam")
    void testRamOnly() {
        hybridStrong.put("s1", "/p", new RamComponent());
        assertThat(l2Redis.memory).isEmpty();
    }

    @Test
    @DisplayName("Debe realizar replace sincrónico y exitoso (STRONG)")
    void testReplaceStrongSuccess() {
        NormalComponent comp = new NormalComponent();
        comp._setVersion(5);
        boolean ok = hybridStrong.replace("s1", "/p", comp, 5);
        assertThat(ok).isTrue();
        assertThat(comp._getVersion()).isEqualTo(6);
        assertThat(l1Ram.memory).hasSize(1);
    }

    @Test
    @DisplayName("Debe invalidar RAM si falla el replace en Redis (STRONG)")
    void testReplaceStrongFail() {
        l1Ram.put("s1", "/p", new NormalComponent());
        l2Redis.replaceResult = false; // Simulamos conflicto
        
        boolean ok = hybridStrong.replace("s1", "/p", new NormalComponent(), 1);
        assertThat(ok).isFalse();
        assertThat(l1Ram.memory).isEmpty(); // Cache sucio limpiado
    }

    @Test
    @DisplayName("Debe reemplazar asíncronamente (EVENTUAL)")
    void testReplaceEventual() throws InterruptedException {
        NormalComponent comp = new NormalComponent();
        comp._setVersion(2);
        
        boolean ok = hybridEventual.replace("s1", "/p", comp, 2);
        assertThat(ok).isTrue();
        assertThat(comp._getVersion()).isEqualTo(3);
        
        Thread.sleep(100); // Esperamos a que Redis termine en background
        assertThat(l2Redis.memory).isNotEmpty();
    }

    @Test
    @DisplayName("Debe borrar asíncronamente sesiones de Redis (EVENTUAL)")
    void testRemoveSessionEventual() throws InterruptedException {
        l1Ram.put("s1", "/p", new NormalComponent());
        l2Redis.put("s1", "/p", new NormalComponent());
        
        hybridEventual.removeSession("s1");
        hybridEventual.remove("s1", "/p2");
        
        Thread.sleep(100);
        assertThat(l1Ram.memory).isEmpty();
        assertThat(l2Redis.memory).isEmpty();
    }
}