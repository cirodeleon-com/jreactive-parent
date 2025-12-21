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

    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    public UndertowJrxSession(WebSocketChannel channel, String sessionId) {
        this.channel = channel;
        this.id = sessionId;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void sendText(String text) {
        if (channel.isOpen()) {
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
            channel.sendClose();
        } catch (IOException e) {
            try { channel.close(); } catch (IOException ignored) {}
        }
    }

    @Override
    public void setAttr(String key, Object val) {
        if (val == null) attributes.remove(key);
        else attributes.put(key, val);
    }

    @Override
    public Object getAttr(String key) {
        return attributes.get(key);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UndertowJrxSession other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
