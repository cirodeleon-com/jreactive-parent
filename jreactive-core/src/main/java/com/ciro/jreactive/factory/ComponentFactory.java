package com.ciro.jreactive.factory;

public interface ComponentFactory {
    <T> T create(Class<T> type);

    default Object create(String id) {
        throw new UnsupportedOperationException("String id factory not implemented");
    }
}
