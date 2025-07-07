package com.ciro.jreactive;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import com.ciro.jreactive.router.RouteRegistry;

@Component
public class PageResolver {
    private final RouteRegistry registry;
    private final Map<String, HtmlComponent> instances = new ConcurrentHashMap<>();

    public PageResolver(RouteRegistry registry) {
        this.registry = registry;
    }

    public HtmlComponent getPage(String path) {
        return instances.computeIfAbsent(path, registry::resolveWithInstance);
    }

    public HtmlComponent getHomePageInstance() {
        return getPage("/");
    }
}


