package com.ciro.jreactive;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class EventBus {
    private final Map<String, List<Consumer<Object>>> map = new ConcurrentHashMap<>();
    public <T> void on(String event, Consumer<T> handler) {
        map.computeIfAbsent(event, __ -> new CopyOnWriteArrayList<>())
           .add((Consumer<Object>) handler);
    }
    public void emit(String event, Object payload) {
        map.getOrDefault(event, List.of()).forEach(h -> h.accept(payload));
    }
}

