package org.jlab.ersap.actor.datatypes;

/**
 * Copyright (c) 2021, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * @author gurjyan on 8/31/22
 * @project ersap-sampa
 */
import org.jlab.epsci.ersap.base.error.ErsapException;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.engine.ErsapSerializer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * <p>
 * This class is used to define the data sent from a Front End Card (FEC) with 5 Sampa chips.
 * It is an array of 160 ByteBuffers. Each buffer corresponds to one channel from a Sampa chip.
 * The format of (de)serialization is as follows:</p>
 * <pre><b>
 *     Number of byte arrays to come --- 32 bit signed int (don't use highest bit)
 *     Length of 1st array in bytes  --- 32 bit signed int (don't use highest bit)
 *     [1st ByteBuffer's bytes]
 *     Length of 2nd array in bytes  --- 32 bit signed int
 *     [2nd ByteBuffer's bytes]
 *     ...
 *     Length of last array in bytes --- 32 bit signed int
 *     [Last ByteBuffer's bytes]
 * </b></pre>
 */
public final class DasDataType {

    private DasDataType() { }


    /**
     * Serialize the given array of ByteBuffers into a single ByteBuffer.
     * <b>The input buffers must be LITTLE endian.</b> This is assumed in this method.
     * The returned buffer is little endian.
     * Called internally.
     * @param buffers buffers to serialize together.
     * @return one buffer containing all data.
     * @throws ErsapException if arg is null.
     */
    public static ByteBuffer serialize(ByteBuffer[] buffers) throws ErsapException {

        if (buffers == null) {
            throw new ErsapException("arg is null");
        }

        // Number of bufs
        int arrayLen = buffers.length;
        // Corresponding number of buffer lengths (of valid bytes)
        int[] len = new int[arrayLen];

        // Total number of bytes of all data.
        // Start with number of ints (1 for total # of bufs, and 1 for each BB len)
        int totalLen = 4*(arrayLen + 1);

        // Add the bytes in each BB
        for (int i=0; i < arrayLen; i++) {
            len[i] = buffers[i].limit();
            totalLen += len[i];
        }

        int writePos = 0;

        // Dealing with little endian data
        ByteBuffer outBuf = ByteBuffer.allocate(totalLen);
        outBuf.order(ByteOrder.LITTLE_ENDIAN);

        outBuf.putInt(writePos, arrayLen);
        writePos += 4;

        for (int i=0; i < arrayLen; i++) {
            outBuf.putInt(writePos, len[i]);
            writePos += 4;
            System.arraycopy(buffers[i].array(), 0, outBuf.array(), writePos, len[i]);
            writePos += len[i];
        }

        // Get ready for reading (pos is already 0)
        outBuf.limit(totalLen);
        return outBuf;
    }



    /**
     * Deserialize the given buffer into an array of ByteBuffers.
     * <b>The buffer data must be LITTLE endian.</b>
     * This is assumed in this method.
     * Called internally.
     * @param buffer buffer to deserialize.
     * @return array of ByteBuffers.
     * @throws ErsapException if arg is null.
     */
    public static ByteBuffer[] deserialize(ByteBuffer buffer) throws ErsapException {

        if (buffer == null) {
            throw new ErsapException("arg is null");
        }

        int readPos = 0;
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        int bufCount = buffer.getInt(readPos);
        readPos += 4;

        // Allocate the array of ByteBuffers
        int len;
        ByteBuffer[] buffers = new ByteBuffer[bufCount];

        for (int i=0; i < bufCount; i++) {
            len = buffer.getInt(readPos);
            readPos += 4;
            buffers[i] = ByteBuffer.allocate(len);
            buffers[i].order(ByteOrder.LITTLE_ENDIAN);
            System.arraycopy(buffer.array(), readPos, buffers[i].array(), 0, len);
            readPos += len;
        }

        return buffers;
    }



    private static class SampaSerializer implements ErsapSerializer {

        /**
         * In actual use, the serialize method above, is called in SMPTwoStreamEngineAggregator.
         * It's called there since the data comes from a ring buffer entry and must be returned,
         * thus it must be copied anyway. Might as well serialize it at the same time as well
         * and save some CPU. So ..., by the time the data gets here, it's already serialized.
         * Just pass it on.
         *
         * @param data data object to be serialized. In this case it's already serialized.
         * @return data serialized into a buffer.
         * @throws ErsapException if arg is null.
         */
        @Override
        public ByteBuffer write(Object data) throws ErsapException {
            if (data == null) throw new ErsapException("arg is null");
            return (ByteBuffer)data;
        }

        @Override
        public Object read(ByteBuffer buffer) throws ErsapException {
            return deserialize(buffer);
        }
    }


    public static final EngineDataType SAMPA_DAS =
            new EngineDataType("binary/data-sampa", new SampaSerializer());
}