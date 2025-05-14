package org.jlab.ersap.actor.coda.source.socket;

/**
 * Event class for use with LMAX Disruptor RingBuffer.
 */
public class Event {
    private byte[] data;

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }
}