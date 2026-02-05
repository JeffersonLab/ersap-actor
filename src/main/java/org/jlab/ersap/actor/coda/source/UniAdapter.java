package org.jlab.ersap.actor.coda.source;
import org.jlab.ersap.actor.util.IASource;

import java.nio.ByteOrder;

public class UniAdapter implements IASource {

    public UniAdapter() {
    }

    @Override
    public Integer nextEvent() {
        return 369;
    }

    @Override
    public int getEventCount() {
        return Integer.MAX_VALUE;
    }

    @Override
    public ByteOrder getByteOrder() {
        return ByteOrder.nativeOrder();
    }

    @Override
    public void close() {
    }
}
