package org.jlab.ersap.actor.sampa.source.decoder;

/**
 * Copyright (c) 2021, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * This interface allows for using 2 different decoders,
 * one for the DSP and the other for the DAS format data from the SAMPA board.
 * They're similar enough to warrant using the same interface.
 * @author gurjyan on 8/31/22
 * @project ersap-sampa
 */

import org.jlab.ersap.actor.sampa.source.ring.SRingRawEvent;
import org.jlab.ersap.actor.sampa.EMode;

/**
 */
public interface IDecoder {

    /**
     * Get the type of Decoder this is.
     * @return type of Decoder this is.
     */
    EMode getSampaType();

    /**
     * Decode a single frame of streamed data from SAMPA card.
     *
     * @param gbt_frame frame of streamed data.
     * @param rawEvent  object from ring buffer for storing data and passing to next ring consumer.
     * @throws Exception thrown if looking for a sync from each stream, but only some are found, while
     *                   at the same time the storage limit for streamed data has been reached.
     *                   Thrown only in DAS mode.
     */
    void decodeSerial(int[] gbt_frame, SRingRawEvent rawEvent) throws Exception;

    /**
     * Get the number of frames that have been processed by this decoder.
     * @return number of frames that have been processed by this decoder.
     */
    long getFrameCount();


    // For DAS mode only

    /** Have the ByteBuffers holding parsed data reached their limit? */
    boolean isFull();

    // For DSP mode only which organizes data into blocks

    /**
     * Get the number of full blocks of data parsed (DSP mode only).
     * @return number of full blocks of data parsed.
     */
    int getBlockCount();

    /**
     * Increment by one the number of full blocks of data parsed (DSP mode only).
     * @return number of full blocks of data parsed after the increment.
     */
    int incrementBlockCount();

}

