package org.jlab.ersap.actor.util;

import java.nio.ByteOrder;

public interface IASource {

    /**
     * Retrieves the next event from the source data ring (ringBuffer)
     *
     * @return an Object representing the next event. The specifics are
     * defined by the class implementing this interface.
     */
    public Object nextEvent();

    public int getEventCount();

    /**
     * Retrieves the byte order used to read or write data to the source.
     *
     * @return a ByteOrder object indicating the byte order used by the source.
     */
    public ByteOrder getByteOrder();

    /**
     * Closes the connection to the resource or source.
     * It should perform any necessary cleanup and resource deallocation.
     */
    public void close();
}