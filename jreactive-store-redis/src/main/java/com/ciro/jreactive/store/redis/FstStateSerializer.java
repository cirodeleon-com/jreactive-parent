package com.ciro.jreactive.store.redis;

import org.nustaq.serialization.FSTConfiguration;

public class FstStateSerializer implements StateSerializer {

    private final FSTConfiguration conf = FSTConfiguration.createDefaultConfiguration();

    @Override
    public byte[] serialize(Object obj) {
        return conf.asByteArray(obj);
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> type) {
        try {
            return type.cast(conf.asObject(bytes));
        } catch (Exception e) {
            throw new RuntimeException("Error deserializando estado FST (Posible cambio de clase)", e);
        }
    }

    @Override
    public String name() {
        return "fst";
    }
}