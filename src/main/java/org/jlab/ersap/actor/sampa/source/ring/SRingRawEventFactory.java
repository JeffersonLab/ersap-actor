package org.jlab.ersap.actor.sampa.source.ring;

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
import com.lmax.disruptor.EventFactory;
import org.jlab.ersap.actor.sampa.EMode;

public class SRingRawEventFactory implements EventFactory<SRingRawEvent> {

    private final EMode type;
    private final int byteSize;
    private final boolean forAggregation;

    /**
     * Constructor of factory which produces event for a disruptor's ring buffer.
     * The events produced by this factory will, by default, have buffers of 131072 bytes
     * and only hold data from a single stream.
     * @param type type of data coming from SAMPA board.
     */
    SRingRawEventFactory(EMode type) {this(type, 131072, false);}

    /**
     * Constructor of factory which produces event for a disruptor's ring buffer.
     * @param type type of data coming from SAMPA board
     * @param byteSize number of bytes in each internal buffer.
     * @param forAggregation if true, this is used to hold aggregated data -
     *                       all 160 channels of a SAMPA board. Or, in other words,
     *                       it needs hold 2x the data coming from a single stream.
     */
    public SRingRawEventFactory(EMode type, int byteSize, boolean forAggregation) {
        this.type = type;
        this.byteSize = byteSize;
        this.forAggregation = forAggregation;
    }

    @Override
    public SRingRawEvent newInstance() {
        return new SRingRawEvent(type, byteSize, forAggregation);
    }
}
