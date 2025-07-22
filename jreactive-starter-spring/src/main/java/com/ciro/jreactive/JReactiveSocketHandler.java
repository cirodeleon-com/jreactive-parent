package com.ciro.jreactive;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class JReactiveSocketHandler extends TextWebSocketHandler {

    private final Map<String, ReactiveVar<?>> bindings;
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper mapper;
    private final ScheduledExecutorService scheduler;
    
    /* --- back-pressure buffer --- */
    private final Map<String,Object> batch = new ConcurrentHashMap<>();
    private volatile boolean flushScheduled = false;

    

    /*  reemplaza TODO el constructor  */
    public JReactiveSocketHandler(ViewNode root, ObjectMapper mapper,ScheduledExecutorService scheduler) {
        this.mapper   = mapper;
        /*  ✅  SIEMPRE recoge recursivamente TODO el árbol  */
        this.bindings = collect(root);
        this.scheduler=scheduler;

        /*  listeners para broadcast */
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
    
    /* ---  agrupa varios cambios en un único flush  --- */
    private void broadcast(String k, Object v) {
        batch.put(k, v);

        if (!flushScheduled) {
            flushScheduled = true;
            scheduler.schedule(this::flushBatch, 30, TimeUnit.MILLISECONDS); // ~33 fps máx.
        }
    }

    private void flushBatch() {
        flushScheduled = false;
        if (batch.isEmpty()) return;

        List<Map<String,Object>> payload = new ArrayList<>();
        batch.forEach((key,val) -> {
            Map<String,Object> m = new HashMap<>();
            m.put("k", key);
            m.put("v", val);
            payload.add(m);
        });
        batch.clear();

        TextMessage msg;
        try { msg = new TextMessage(mapper.writeValueAsString(payload)); }
        catch (IOException ex) { return; }
        
        if (mapper != null) {   // evita NPE en tests
            //System.out.println("[WS batch] " + msg.getPayload());
        }


        sessions.removeIf(sess -> {
            if (!sess.isOpen()) return true;
            try {
                synchronized (sess) { sess.sendMessage(msg); }
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

        // 1) cualquier ViewLeaf (incluye HtmlComponent)
        if (node instanceof ViewLeaf leaf) {
            map.putAll(leaf.bindings());
        }

        // 2) si además es HtmlComponent, recorrer sus hijos vivos
        if (node instanceof HtmlComponent hc) {
            for (HtmlComponent child : hc._children()) {
                map.putAll(collect(child));          // recursivo
            }
        }

        // 3) ViewComposite también puede tener hijos ViewNode
        if (node instanceof ViewComposite comp) {
            for (ViewNode child : comp.children()) {
                map.putAll(collect(child));          // recursivo
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
