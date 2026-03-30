package com.ciro.jreactive;

import com.ciro.jreactive.smart.SmartList;
import com.ciro.jreactive.spi.JrxMessageBroker;
import com.ciro.jreactive.spi.JrxSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JrxProtocolHandler - Deep Updates Seguros")
class JrxProtocolHandlerTest {

    @Mock JrxSession session;
    @Mock JrxMessageBroker broker;
    private static ObjectMapper mapper = new ObjectMapper();
    private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // 🔥 Usamos un contenedor público explícito para asegurar la inyección
    public static class ContenedorDeep {
        @State public String secreto = "Inicial";
        public String role = "GUEST";
    }

    static class ComplexPage extends HtmlComponent {
        @State public ContenedorDeep dto = new ContenedorDeep(); 
        @State public SmartList<String> tags = new SmartList<>();
        @Override protected String template() { return "<div>{{dto.secreto}}</div>"; }
    }

    static class SharedPage extends HtmlComponent {
        @State public String localData = "Local";
        @State public String sharedData = "Global"; 
        @Override protected String template() { return "<div>{{localData}}</div>"; }
    }

    @BeforeAll
    static void init() {
        AstComponentEngine.installAsDefault();
    }

    @Test
    @DisplayName("Debe actualizar propiedades profundamente anidadas sin backpressure")
    void testDeepUpdateNoBackpressure() {
        ComplexPage page = new ComplexPage();
        page._initIfNeeded();
        page._mountRecursive();
        
        JrxProtocolHandler handler = new JrxProtocolHandler(page, mapper, null, false, 100, 16, null, null);
        lenient().when(session.isOpen()).thenReturn(true);
        handler.onOpen(session, null, 0);

        // Simulamos un update profundo desde el cliente JS
        handler.onMessage(session, "{\"k\":\"dto.secreto\", \"v\":\"Hackeado\"}");

        // Verificamos que el valor cambió
        assertThat(page.dto.secreto).isEqualTo("Hackeado");
    }

    @Test
    @DisplayName("Debe ejecutar el flush de la cola y consolidar deltas de Backpressure")
    void testQueueFlush() throws Exception {
        ComplexPage page = new ComplexPage();
        page._initIfNeeded();
        page._mountRecursive(); 
        
        JrxProtocolHandler handler = new JrxProtocolHandler(page, mapper, scheduler, true, 10, 50, null, null);
        lenient().when(session.isOpen()).thenReturn(true);
        handler.onOpen(session, null, 0);
        
        // Mutamos el estado
        page.dto.role = "ADMIN";
        page._syncState();
        page.tags.add("Nuevo Tag"); 

        Thread.sleep(150); // Damos tiempo al flush

        verify(session, atLeastOnce()).sendText(anyString());
    }

    @Test
    @DisplayName("Debe ignorar actualizaciones de propiedades prohibidas (class, classLoader)")
    void testForbiddenPropertyUpdate() {
        ComplexPage page = new ComplexPage();
        page._initIfNeeded();
        page._mountRecursive();
        
        JrxProtocolHandler handler = new JrxProtocolHandler(page, mapper, scheduler, false, 100, 16, null, broker);
        
        // Ataque Reflection
        handler.onMessage(session, "{\"k\":\"dto.class\", \"v\":\"Hack\"}");

        assertThat(page.dto.role).isEqualTo("GUEST");
    }

    @Test
    @DisplayName("Debe publicar al Broker cuando cambia una variable compartida (@Shared)")
    void testSharedVariablePublishing() {
        SharedPage page = new SharedPage();
        page._initIfNeeded();
        page._mountRecursive();
        
        ReactiveVar<Object> rv = (ReactiveVar<Object>) page.getRawBindings().get("sharedData");
        rv.setSharedTopic("mi-sala");

        JrxProtocolHandler handler = new JrxProtocolHandler(page, mapper, scheduler, false, 100, 16, null, broker);
        
        page.sharedData = "Nuevo Global";
        page._syncState(); 

        verify(broker, atLeastOnce()).publishShared(eq("mi-sala"), anyString());
    }

    @Test
    @DisplayName("Debe ignorar JSON malformado sin tumbar el servidor")
    void testMalformedJsonHandling() {
        SharedPage page = new SharedPage();
        page._initIfNeeded();
        
        JrxProtocolHandler handler = new JrxProtocolHandler(page, mapper, scheduler, false, 100, 16, null, broker);
        
        handler.onMessage(session, "{esto_no_es_json}");

        assertThat(page.localData).isEqualTo("Local");
    }

    @Test
    @DisplayName("Debe limpiar recursos al cerrar la sesión")
    void testOnClose() {
        ComplexPage page = new ComplexPage();
        page._initIfNeeded();
        page._mountRecursive();

        JrxProtocolHandler handler = new JrxProtocolHandler(page, mapper, null, false, 100, 16, null, null);
        
        handler.onOpen(session, null, 0);
        handler.onClose(session); 
        
        page.dto.role = "USER";
        page._syncState();
        
        verify(session, never()).sendText(contains("USER"));
    }
}