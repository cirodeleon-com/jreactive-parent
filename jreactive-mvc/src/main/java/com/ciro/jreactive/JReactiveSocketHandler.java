package com.ciro.jreactive;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class JReactiveSocketHandler extends TextWebSocketHandler {

    private final Map<String, ReactiveVar<?>> bindings;
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper mapper = new ObjectMapper();

    public JReactiveSocketHandler(ViewNode root) {
        this.bindings = collect(root);
        bindings.forEach((k, v) -> v.onChange(val -> broadcast(k, val)));
    }

    /* --- 1. registrar nueva sesión y enviar snapshot --- */
    @Override
    public void afterConnectionEstablished(WebSocketSession s) throws Exception {
        sessions.add(s);

        for (var e : bindings.entrySet()) {
            if (s.isOpen()) {
                s.sendMessage(json(e.getKey(), e.getValue().get()));
            }
        }
    }

    /* --- 2. retirar la sesión cerrada --- */
    @Override
    public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
        sessions.remove(s);
    }

    /* --- 3. recibir evento cliente → servidor --- */
    @Override
    protected void handleTextMessage(WebSocketSession s, TextMessage msg) throws Exception {
        Map<String, String> m = mapper.readValue(msg.getPayload(), Map.class);
        ReactiveVar<Object> rv = (ReactiveVar<Object>) bindings.get(m.get("k"));
        if (rv != null) rv.set(m.get("v"));
    }

    /* --- 4. broadcast seguro a todos los clientes conectados --- */
    //private void broadcast(String k, Object v) {
    //    broadcast(k, v, "state");
    //}
    //private TextMessage json(String k, Object v) {
    //    return json(k, v, "state");
    //}
    
    private void broadcast(String k, Object v) {
        TextMessage payload = json(k, v);
        sessions.removeIf(s -> {
            if (!s.isOpen()) return true;
            try {
                synchronized (s) {          
                    s.sendMessage(payload);
                    System.out.println("🔁 Enviado a cliente: " + payload);
                }
            } catch (IOException ex) { return true; }
            return false;
        });
    }


    private TextMessage json(String k, Object v) {
        try {
            Map<String,Object> payload = new HashMap<>();
            payload.put("k", k);         // clave nunca null en tu diseño
            payload.put("v", v);         // puede ser null
            //payload.put("type",type);
            return new TextMessage(mapper.writeValueAsString(payload));
        } catch (IOException e) {
            throw new RuntimeException("Error serializing WebSocket message", e);
        }
    }

    
    /* -------------------------------------------------
     * Reúne los bindings de TODO el árbol de componentes
     * ------------------------------------------------- */
    private Map<String, ReactiveVar<?>> collect(ViewNode node) {
        Map<String, ReactiveVar<?>> map = new HashMap<>();

        if (node instanceof ViewLeaf leaf) {                    // hoja
            map.putAll(leaf.bindings());
        } else if (node instanceof ViewComposite composite) {   // compuesto
            for (ViewNode child : composite.children()) {
                map.putAll(collect(child));                     // recursivo
            }
        }
        return map;
    }
    
    /*
    public void emitEvent(String eventName, Object payload) {
        broadcast(eventName, payload, "event");
    }
    */

}
