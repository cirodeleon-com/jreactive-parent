package com.ciro.jreactive;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import com.ciro.jreactive.annotations.StatefulRam; 

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

@Component
public class DelegatingWebSocketHandler implements WebSocketHandler {

    private final PageResolver pageResolver;
    private final ObjectMapper mapper;
    private final ScheduledExecutorService scheduler;
    private final WsConfig wsConfig;
    // 👇 Nuevo
    private final JrxHubManager hubManager; 

    public DelegatingWebSocketHandler(PageResolver pageResolver,
                                      ObjectMapper mapper,
                                      ScheduledExecutorService scheduler,
                                      WsConfig wsConfig,
                                      JrxHubManager hubManager) { // <--- Inyectado por Spring
        this.pageResolver = pageResolver;
        this.mapper = mapper;
        this.scheduler = scheduler;
        this.wsConfig = wsConfig;
        this.hubManager = hubManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String path = (String) session.getAttributes().get("path");
        if (path == null) path = "/";

        String sessionId = (String) session.getAttributes().get("sessionId");
        if (sessionId == null) {
            sessionId = session.getId();
            session.getAttributes().put("sessionId", sessionId);
        }

        HtmlComponent page = pageResolver.getPage(sessionId, path);
        
        boolean isStatefulRam = page.getClass().isAnnotationPresent(StatefulRam.class);
        session.getAttributes().put("isStatefulRam", isStatefulRam);

        // 🔥 CAMBIO: Ahora pasamos hubManager, path y sessionId
        JReactiveSocketHandler delegate = new JReactiveSocketHandler(
            page, mapper, scheduler, wsConfig,
            hubManager, path, sessionId,pageResolver
        );
        
        delegate.afterConnectionEstablished(session);

        session.getAttributes().put("delegate", delegate);
    }

    // ... (El resto de métodos handleMessage, handleTransportError, etc. QUEDAN IGUALES) ...
    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        var delegate = (WebSocketHandler) session.getAttributes().get("delegate");
        if (delegate != null) delegate.handleMessage(session, message);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        var delegate = (WebSocketHandler) session.getAttributes().get("delegate");
        if (delegate != null) delegate.handleTransportError(session, exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        var delegate = (WebSocketHandler) session.getAttributes().get("delegate");
        if (delegate != null) delegate.afterConnectionClosed(session, status);
        
        Boolean isStatefulRam = (Boolean) session.getAttributes().get("isStatefulRam");
        //boolean isStatefulRam = page.isStatefulRam();

        // Si el modo es EFÍMERO (false), limpiamos la RAM al cerrar
        if (Boolean.TRUE.equals(isStatefulRam) || !wsConfig.isPersistentState()) { 
            String path = (String) session.getAttributes().get("path");
            String sessionId = (String) session.getAttributes().get("sessionId");

            // 🔥 CORRECCIÓN: 
            // SockJS a veces no envía el "reason" ("route-change"), pero SÍ envía el código.
            // Código 1000 = NORMAL (Navegación del usuario o close() manual).
            // Código 1001 = GOING_AWAY (Cierre de pestaña/navegador).
            // Código 1006 = ABNORMAL (Fallo de red -> NO limpiamos para permitir reconexión).
            
            boolean isIntentionalClose = status.getCode() == 1000 || status.getCode() == 1001;
            
            // También mantenemos el chequeo de texto por si acaso viaja por WebSocket puro
            boolean hasReason = "route-change".equals(status.getReason());

            if (path != null && sessionId != null && (isIntentionalClose || hasReason)) {
                System.out.println("🧹 [Ephemereal] Limpiando estado para: " + path + " (Code: " + status.getCode() + ")");
                
                // 1. Borrar de la RAM (PageResolver -> StateStore)
                pageResolver.evict(sessionId, path);
                
                // 2. Borrar colas de mensajes pendientes (HubManager)
                hubManager.evict(sessionId, path);
            }
        }
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}