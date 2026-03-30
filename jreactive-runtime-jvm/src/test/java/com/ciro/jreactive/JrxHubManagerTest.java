package com.ciro.jreactive;

import com.ciro.jreactive.spi.JrxMessageBroker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll; // 👈 Importante
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JrxHubManager - Gestión de Ciclo de Vida de WebSockets")
class JrxHubManagerTest {

    @Mock PageResolver pageResolver;
    @Mock JrxMessageBroker broker;
    
    private ObjectMapper mapper = new ObjectMapper();
    private JrxHubManager manager;

    static class TestPage extends HtmlComponent {
        @Override protected String template() { return "<div></div>"; }
    }

    @BeforeAll
    static void initFramework() {
        // 🔥 LA CLAVE: Instalamos el motor de renderizado para que render() no explote
        AstComponentEngine.installAsDefault();
    }

    @BeforeEach
    void setUp() {
        manager = new JrxHubManager(pageResolver, mapper, broker);
    }

    @Test
    @DisplayName("Debe crear un Hub nuevo y reutilizarlo si la sesión es la misma")
    void testHubCache() {
        TestPage page = new TestPage();
        when(pageResolver.getPage(anyString(), anyString())).thenReturn(page);

        // Primer pedido: crea
        JrxPushHub hub1 = manager.hub("sid-1", "/home");
        // Segundo pedido: saca de caché
        JrxPushHub hub2 = manager.hub("sid-1", "/home");

        assertThat(hub1).isNotNull();
        assertThat(hub1).isSameAs(hub2);
    }

    @Test
    @DisplayName("Debe reciclar el Hub (Zombie Check) si la instancia de la página cambió")
    void testZombieRecycling() {
        TestPage pageV1 = new TestPage();
        TestPage pageV2 = new TestPage();

        when(pageResolver.getPage("sid-1", "/")).thenReturn(pageV1);
        JrxPushHub hub1 = manager.hub("sid-1", "/");

        when(pageResolver.getPage("sid-1", "/")).thenReturn(pageV2);
        JrxPushHub hub2 = manager.hub("sid-1", "/");

        assertThat(hub1).isNotSameAs(hub2);
        assertThat(hub2.getPageInstance()).isSameAs(pageV2);
    }

    @Test
    @DisplayName("Debe limpiar hubs al desalojar sesiones (evict)")
    void testEviction() {
        TestPage page = new TestPage();
        when(pageResolver.getPage(anyString(), anyString())).thenReturn(page);

        JrxPushHub hub = manager.hub("sid-99", "/p");
        manager.evict("sid-99", "/p");

        JrxPushHub hubNuevo = manager.hub("sid-99", "/p");
        assertThat(hub).isNotSameAs(hubNuevo);
    }
    
    @Test
    @DisplayName("Debe desalojar todas las sesiones y sincronizar páginas desfasadas")
    void testEvictAllAndEnsureSync() {
        TestPage page1 = new TestPage();
        TestPage page2 = new TestPage(); // Simulamos una versión más nueva de la página

        when(pageResolver.getPage(anyString(), anyString())).thenReturn(page1);
        
        // Creamos el hub
        JrxPushHub hub = manager.hub("sid-multi", "/dashboard");
        
        // Forzamos un sync con una página distinta (detecta desfase y hace rebind)
        manager.ensureSync("sid-multi", "/dashboard", page2);
        
        // El hub debió actualizar su página interna a page2
        assertThat(hub.getPageInstance()).isSameAs(page2);

        // Desalojamos todo lo de ese sessionId
        manager.evictAll("sid-multi");
        
        // Al volver a pedir, debería crear uno nuevo porque se borró de la caché
        when(pageResolver.getPage(anyString(), anyString())).thenReturn(page2);
        JrxPushHub hubNuevo = manager.hub("sid-multi", "/dashboard");
        
        assertThat(hubNuevo).isNotSameAs(hub);
    }
    
    @Test
    @DisplayName("Debe cubrir equals, hashCode y métodos de la clase interna privada Key")
    void testHubManagerKeyInnerClass() throws Exception {
        // Obtenemos la clase privada Key
        Class<?> keyClass = Class.forName("com.ciro.jreactive.JrxHubManager$Key");
        java.lang.reflect.Constructor<?> constructor = keyClass.getDeclaredConstructor(String.class, String.class);
        constructor.setAccessible(true);
        
        // Instanciamos objetos
        Object key1 = constructor.newInstance("sid1", "/path");
        Object key2 = constructor.newInstance("sid1", "/path");
        Object key3 = constructor.newInstance("sid2", "/path");

        // Probamos equals y hashCode
        assertThat(key1.equals(key2)).isTrue();
        assertThat(key1.equals(key3)).isFalse();
        assertThat(key1.equals(null)).isFalse();
        assertThat(key1.hashCode()).isEqualTo(key2.hashCode());
        
        // Probamos los getters internos
        java.lang.reflect.Method mSid = keyClass.getDeclaredMethod("sessionId");
        mSid.setAccessible(true);
        assertThat(mSid.invoke(key1)).isEqualTo("sid1");

        java.lang.reflect.Method mPath = keyClass.getDeclaredMethod("path");
        mPath.setAccessible(true);
        assertThat(mPath.invoke(key1)).isEqualTo("/path");
    }
}