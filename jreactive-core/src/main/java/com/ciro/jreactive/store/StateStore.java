package com.ciro.jreactive.store;

import com.ciro.jreactive.HtmlComponent;

public interface StateStore {
    HtmlComponent get(String sessionId, String path);
    void put(String sessionId, String path, HtmlComponent component);
    boolean replace(String sessionId, String path, HtmlComponent component, long expectedVersion);
    void remove(String sessionId, String path);
    void removeSession(String sessionId);
}