package com.ciro.jreactive;

import java.util.Map;

public abstract class ViewLeaf implements ViewNode {
    public abstract Map<String, ReactiveVar<?>> bindings();
}
