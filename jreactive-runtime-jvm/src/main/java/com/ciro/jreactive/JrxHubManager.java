package com.ciro.jreactive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.*;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class JrxHubManager {

	private static final class Key {
	    private final String sessionId;
	    private final String path;

	    Key(String sessionId, String path) {
	        this.sessionId = sessionId;
	        this.path = path;
	    }

	    String sessionId() { return sessionId; }
	    String path() { return path; }

	    @Override public boolean equals(Object o) {
	        if (this == o) return true;
	        if (!(o instanceof Key)) return false;
	        Key k = (Key) o;
	        return Objects.equals(sessionId, k.sessionId) && Objects.equals(path, k.path);
	    }

	    @Override public int hashCode() {
	        return Objects.hash(sessionId, path);
	    }
	}


    private final PageResolver pageResolver;
    private final ObjectMapper mapper;
    private final Cache<Key, JrxPushHub> hubs;

    public JrxHubManager(PageResolver pageResolver, ObjectMapper mapper) {
        this.pageResolver = pageResolver;
        this.mapper = mapper;

        this.hubs = Caffeine.newBuilder()
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .maximumSize(5_000)
                .removalListener((Key k, JrxPushHub hub, RemovalCause cause) -> {
                    if (hub != null) hub.close();
                })
                .build();
    }

    public JrxPushHub hub(String sessionId, String path) {
        Key key = new Key(sessionId, path);
        return hubs.get(key, _k -> {
            HtmlComponent page = pageResolver.getPage(sessionId, path);
            return new JrxPushHub(page, mapper, 2_000);
        });
    }

    public void evictAll(String sessionId) {
        hubs.asMap().keySet().removeIf(k -> k.sessionId().equals(sessionId));
    }
}
