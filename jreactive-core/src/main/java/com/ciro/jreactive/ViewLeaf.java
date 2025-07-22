package com.ciro.jreactive;

import java.util.Map;

public abstract class ViewLeaf implements ViewNode {
    public abstract Map<String, ReactiveVar<?>> bindings();
    private String fixedId;
    private static long COUNTER = 0;

    public void setId(String id) {
        this.fixedId = id;
    }

    public String getId() {
        return fixedId != null ? fixedId : getClass().getSimpleName() + "#" + (++COUNTER);
    }
}
