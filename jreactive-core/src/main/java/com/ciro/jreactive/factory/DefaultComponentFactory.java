package com.ciro.jreactive.factory;

import java.lang.reflect.Constructor;

public class DefaultComponentFactory implements ComponentFactory {

    @Override
    public <T> T create(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("type cannot be null");
        }

        try {
            Constructor<T> ctor = type.getDeclaredConstructor();
            if (!ctor.canAccess(null)) {
                ctor.setAccessible(true);
            }
            return ctor.newInstance();
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    "No-arg constructor not found for component: " + type.getName(), e
            );
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to instantiate component: " + type.getName(), e
            );
        }
    }
}
