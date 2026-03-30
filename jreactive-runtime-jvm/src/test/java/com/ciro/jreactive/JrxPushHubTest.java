package com.ciro.jreactive;

import com.ciro.jreactive.smart.SmartList;
import com.ciro.jreactive.smart.SmartMap;
import com.ciro.jreactive.smart.SmartSet;
import com.ciro.jreactive.spi.JrxMessageBroker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("JrxPushHub - Cobertura Extrema de Colecciones y Buffer")
class JrxPushHubTest {

    @Mock JrxMessageBroker broker;
    private ObjectMapper mapper = new ObjectMapper();

    // Componente con todos los tipos de colecciones Smart para probar inyección
    static class ColeccionesPage extends HtmlComponent {
        @State public String texto = "A";
        @State public SmartList<String> lista = new SmartList<>();
        @State public SmartMap<String, String> mapa = new SmartMap<>();
        @State public SmartSet<String> set = new SmartSet<>();
        @Override protected String template() { return "<div></div>"; }
    }

    static class MockSink implements JrxPushHub.JrxSink {
        public List<String> sentMessages = new ArrayList<>();
        public boolean open = true;
        @Override public boolean isOpen() { return open; }
        @Override public void send(String json) { sentMessages.add(json); }
        @Override public void close() { open = false; }
    }

    @BeforeAll
    static void init() {
        AstComponentEngine.installAsDefault();
    }

    @Test
    @DisplayName("Debe capturar cambios en @State y enviarlos")
    void testPushNotification() {
        ColeccionesPage page = new ColeccionesPage();
        page._initIfNeeded();
        page._mountRecursive();
        JrxPushHub hub = new JrxPushHub(page, mapper, 100, broker, "sid-1", null);
        MockSink sink = new MockSink();
        hub.subscribe(sink, 0); 
        
        int countBefore = sink.sentMessages.size();
        page.texto = "B";
        page._syncState(); 

        assertThat(sink.sentMessages.size()).isGreaterThan(countBefore);
        assertThat(sink.sentMessages.get(sink.sentMessages.size()-1)).contains("B");
    }

    @Test
    @DisplayName("Debe permitir recuperar historial (Poll) y manejar desbordamiento de Buffer")
    void testHistoryPollAndOverflow() {
        ColeccionesPage page = new ColeccionesPage();
        page._initIfNeeded();
        page._mountRecursive();
        
        // Hub con buffer muy pequeño para forzar overflow
        JrxPushHub hub = new JrxPushHub(page, mapper, 2, broker, "sid-1", null);
        
        long base = hub.snapshot().getSeq();

        page.texto = "C1"; page._syncState();
        page.texto = "C2"; page._syncState();
        page.texto = "C3"; page._syncState(); // Aquí desborda el buffer de 2
        
        // Pedimos desde la base. Como el buffer se desbordó, el hub debería darnos un snapshot
        JrxPushHub.Batch missed = hub.poll(base);
        assertThat(missed.getBatch()).isNotEmpty();
        
        // El último valor debe estar ahí
        assertThat(missed.getBatch().toString()).contains("C3");
    }

    @Test
    @DisplayName("Debe inyectar estado compartido de Redis en SmartCollections en caliente")
    void testInjectSharedStateCollections() {
        ColeccionesPage page = new ColeccionesPage();
        page._initIfNeeded();
        page._mountRecursive();

        // Asignamos topics
        ((ReactiveVar<?>) page.getRawBindings().get("lista")).setSharedTopic("topic-lista");
        ((ReactiveVar<?>) page.getRawBindings().get("mapa")).setSharedTopic("topic-mapa");
        ((ReactiveVar<?>) page.getRawBindings().get("set")).setSharedTopic("topic-set");

        JrxPushHub hub = new JrxPushHub(page, mapper, 100, broker, "sid-1", null);

        // Simulamos mensajes entrantes de Redis Pub/Sub
        hub.injectSharedState("topic-lista", "{\"k\":\"lista\", \"v\":[\"Item1\"]}");
        hub.injectSharedState("topic-mapa", "{\"k\":\"mapa\", \"v\":{\"Key1\":\"Val1\"}}");
        hub.injectSharedState("topic-set", "{\"k\":\"set\", \"v\":[\"Unico\"]}");

        assertThat(page.lista).containsExactly("Item1");
        assertThat(page.mapa).containsEntry("Key1", "Val1");
        assertThat(page.set).contains("Unico");
    }

    @Test
    @DisplayName("Debe hidratar el historial desde el Broker al iniciar (hydrateFromBroker)")
    void testHydrateFromBrokerOnInit() {
        ColeccionesPage page = new ColeccionesPage();
        page._initIfNeeded();
        page._mountRecursive(); // 🔥 EL ARREGLO ESTÁ AQUÍ

        ((ReactiveVar<?>) page.getRawBindings().get("texto")).setSharedTopic("topic-texto");

        // Simulamos que Redis ya tiene el dato guardado
        lenient().when(broker.getSharedState("topic-texto")).thenReturn(Map.of(
            "texto", "\"TextoDesdeRedis\""
        ));

        // Al crear el hub, debe leer del broker y poblar la página
        JrxPushHub hub = new JrxPushHub(page, mapper, 100, broker, "sid-1", null);

        assertThat(page.texto).isEqualTo("TextoDesdeRedis");
    }

    @Test
    @DisplayName("Debe permitir cambiar la instancia vigilada en caliente (rebind)")
    void testRebind() {
        ColeccionesPage page1 = new ColeccionesPage();
        page1._initIfNeeded();
        page1._mountRecursive(); // 🔥 EL ARREGLO ESTÁ AQUÍ
        
        JrxPushHub hub = new JrxPushHub(page1, mapper, 100, broker, "sid-1", null);
        MockSink sink = new MockSink();
        hub.subscribe(sink, 0); 
        
        ColeccionesPage page2 = new ColeccionesPage();
        page2._initIfNeeded();
        page2._mountRecursive(); // 🔥 EL ARREGLO ESTÁ AQUÍ
        hub.rebind(page2);

        page2.texto = "Página 2 activada";
        page2._syncState(); 

        // El sink debió recibir el cambio de la página 2
        assertThat(sink.sentMessages.toString()).contains("Página 2 activada");
        assertThat(hub.getPageInstance()).isSameAs(page2);
    }

    @Test
    @DisplayName("Debe limpiar recursos y desuscribir clientes al hacer close y unsubscribe")
    void testUnsubscribeAndClose() {
        ColeccionesPage page = new ColeccionesPage();
        page._initIfNeeded();
        page._mountRecursive(); // Aseguramos el ciclo de vida completo
        
        JrxPushHub hub = new JrxPushHub(page, mapper, 100, broker, "sid-1", null);
        
        MockSink sink = new MockSink();
        hub.subscribe(sink, 0); 
        
        hub.unsubscribe(sink);
        assertThat(sink.open).isFalse(); 

        int sizeBefore = sink.sentMessages.size();
        page.texto = "Fantasma";
        page._syncState();
        
        assertThat(sink.sentMessages.size()).isEqualTo(sizeBefore); // No recibe más mensajes

        hub.close();
    }
}