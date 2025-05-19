package org.jlab.ersap.actor.coda.source.socket;

import java.nio.ByteBuffer;

/**
 * Event class for use with LMAX Disruptor RingBuffer.
 */
public class Event {
    private byte[] data;

    public ByteBuffer getData() {

        return ByteBuffer.wrap(data);
    }

    public void setData(byte[] data) {
        this.data = data;
    }
}