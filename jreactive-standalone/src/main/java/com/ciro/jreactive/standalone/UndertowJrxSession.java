package com.ciro.jreactive.standalone;

import com.ciro.jreactive.spi.JrxSession;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UndertowJrxSession implements JrxSession {

    private final WebSocketChannel channel;
    private final String id;
    
    // 🔥 Necesitamos un mapa para simular los atributos de sesión (como HttpSession)
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    public UndertowJrxSession(WebSocketChannel channel) {
        this.channel = channel;
        // Generamos un ID único combinando la dirección y el hash del objeto
        this.id = channel.getSourceAddress().toString() + "@" + System.identityHashCode(channel);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void sendText(String text) {
        if (channel.isOpen()) {
            // sendText es asíncrono en Undertow
            WebSockets.sendText(text, channel, null);
        }
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public void close() {
        try {
            // Iniciamos el cierre ordenado del WebSocket
            channel.sendClose();
        } catch (IOException e) {
            // Si falla, intentamos cierre forzoso
            try { channel.close(); } catch (IOException ignored) {}
        }
    }

    @Override
    public void setAttr(String key, Object val) {
        if (val == null) {
            attributes.remove(key);
        } else {
            attributes.put(key, val);
        }
    }

    @Override
    public Object getAttr(String key) {
        return attributes.get(key);
    }
}