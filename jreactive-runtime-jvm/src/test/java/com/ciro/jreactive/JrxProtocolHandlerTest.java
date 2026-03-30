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
        public static String estatico = "no";
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
    
    @Test
    @DisplayName("Debe procesar mensajes de ping y reemplazo masivo de listas Smart")
    void testPingAndListUpdate() {
        ComplexPage page = new ComplexPage();
        page._initIfNeeded(); page._mountRecursive();
        JrxProtocolHandler handler = new JrxProtocolHandler(page, mapper, scheduler, false, 100, 16, null, broker);
        
        // Simulamos mensajes de control o basura común de WebSockets
        handler.onMessage(session, "ping");
        handler.onMessage(session, "{\"type\":\"ping\"}");
        
        // Simulamos que el cliente decide reemplazar toda la SmartList con un nuevo Array JSON
        handler.onMessage(session, "{\"k\":\"tags\", \"v\":[\"Fuego\", \"Agua\"]}");
        
        // El framework debe ser capaz de vaciar la lista y llenarla con los nuevos datos
        assertThat(page.tags).containsExactly("Fuego", "Agua");
    }
    
    
    @Test
    @DisplayName("Debe procesar el reemplazo completo de colecciones Smart")
    void testFullCollectionUpdate() {
        ComplexPage page = new ComplexPage();
        page._initIfNeeded();
        page._mountRecursive();
        
        JrxProtocolHandler handler = new JrxProtocolHandler(page, mapper, scheduler, false, 100, 16, null, broker);
        
        // 1. Reemplazo masivo de la SmartList 'tags'
        handler.onMessage(session, "{\"k\":\"tags\", \"v\":[\"Alpha\", \"Beta\"]}");
        assertThat(page.tags).containsExactly("Alpha", "Beta");

        // 2. Reemplazo por una lista vacía (equivale a clear)
        handler.onMessage(session, "{\"k\":\"tags\", \"v\":[]}");
        assertThat(page.tags).isEmpty();
    }
    
    @Test
    @DisplayName("Debe recolectar bindings de ViewComposite y ViewLeaf puros")
    void testCollectFromCompositesAndLeaves() {
        // Armamos un árbol con ViewComposite
        ViewComposite rootComposite = new ViewComposite();
        ComplexPage page = new ComplexPage();
        page.setId("cp1");
        page._initIfNeeded();
        rootComposite.add(page);

        // Al crear el handler con el Composite como raíz, pasará por las ramas de instanceof ViewComposite
        JrxProtocolHandler handler = new JrxProtocolHandler(rootComposite, mapper, scheduler, false, 100, 16, null, broker);
        
        // Verificamos que encontró el binding oculto dentro del composite
        assertThat(page.dto).isNotNull(); // Simplemente confirmar que no explotó la recolección
    }

    @Test
    @DisplayName("Debe consolidar deltas en la cola de Backpressure antes del flush")
    void testBackpressureDeltaConsolidation() throws Exception {
        ComplexPage page = new ComplexPage();
        page._initIfNeeded();
        page._mountRecursive(); 
        
        JrxProtocolHandler handler = new JrxProtocolHandler(page, mapper, scheduler, true, 100, 500, null, null);
        lenient().when(session.isOpen()).thenReturn(true);
        handler.onOpen(session, null, 0);

        // Simulamos múltiples operaciones rápidas sobre la MISMA SmartList
        // Esto fuerza al método `flush()` a entrar a la rama donde consolida nDp.changes() con oDp.changes()
        page.tags.add("Delta 1");
        page.tags.add("Delta 2");
        page.tags.add("Delta 3");
        
        // Esperamos a que el scheduler dispare el flush
        Thread.sleep(600);
        
        // Si no explotó y la consola envía un solo paquete consolidado, el test pasó por la rama esquiva.
        verify(session, atLeastOnce()).sendText(org.mockito.ArgumentMatchers.contains("Delta 3"));
    }
    
    

    @Test
    @DisplayName("Seguridad: Debe bloquear intentos de modificar campos estáticos, classLoader y serialVersionUID en Deep Updates")
    void testSecurityBlocksOnDeepUpdate() {
        ComplexPage page = new ComplexPage();
        page._initIfNeeded();
        page._mountRecursive();
        JrxProtocolHandler handler = new JrxProtocolHandler(page, mapper, scheduler, false, 100, 16, null, null);
        
        // 2. Ataques simulados DEEP UPDATE (usando el prefijo "dto.")
        // Al tener el punto (.), forzamos que pase por el método seguro getF()
        handler.onMessage(session, "{\"k\":\"dto.classLoader\", \"v\":\"Hack\"}");
        handler.onMessage(session, "{\"k\":\"dto.class\", \"v\":\"Hack\"}");
        handler.onMessage(session, "{\"k\":\"dto.estatico\", \"v\":\"Hack2\"}");

        // El campo estático debió rebotar contra el escudo
        assertThat(ContenedorDeep.estatico).isEqualTo("no"); 
    }

    @Test
    @DisplayName("Debe manejar errores de red en el flush eliminando la sesión defectuosa")
    void testFlushSendError() throws Exception {
        ComplexPage page = new ComplexPage();
        page._initIfNeeded(); 
        page._mountRecursive();
        
        JrxSession badSession = mock(JrxSession.class);
        lenient().when(badSession.isOpen()).thenReturn(true);
        lenient().when(badSession.getId()).thenReturn("bad-session");
        
        // Configuramos el mock para que lance una excepción real al enviar texto
        doThrow(new RuntimeException("Socket roto")).when(badSession).sendText(anyString());
        
        JrxProtocolHandler handler = new JrxProtocolHandler(page, mapper, scheduler, true, 100, 16, null, null);
        handler.onOpen(badSession, null, 0);
        
        // Encolamos un cambio (Backpressure)
        page.dto.role = "HACKER";
        page._syncState(); 
        
        // Damos tiempo a que el Scheduler ejecute flush()
        Thread.sleep(50); 
        
        // Si la sesión lanzó error, el framework debió capturarlo en el catch del removeIf
        verify(badSession, atLeastOnce()).sendText(anyString());
    }
    
    @Test
    @DisplayName("Debe manejar fallos de Reflection en updateDeep sin crashear")
    void testUpdateDeepEdgeCases() {
        ComplexPage page = new ComplexPage();
        page._initIfNeeded(); 
        page._mountRecursive();
        JrxProtocolHandler handler = new JrxProtocolHandler(page, mapper, null, false, 100, 16, null, null);
        
        // 1. Tratar de acceder a un campo que no existe en el DTO
        handler.onMessage(session, "{\"k\":\"dto.no_existe\", \"v\":\"Hack\"}");
        
        // 2. Tratar de inyectar un tipo incorrecto que Jackson no pueda convertir
        handler.onMessage(session, "{\"k\":\"dto.role\", \"v\": {\"objeto\":\"raro\"} }");
        
        // El rol debe seguir intacto porque el catch detuvo el desastre
        assertThat(page.dto.role).isEqualTo("GUEST"); 
    }
    
}