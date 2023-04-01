package org.jlab.ersap.actor.sampa.source;

import com.lmax.disruptor.*;
import org.jlab.epsci.ersap.base.error.ErsapException;
import org.jlab.ersap.actor.datatypes.DasDataType;
import org.jlab.ersap.actor.sampa.EMode;
import org.jlab.ersap.actor.sampa.source.recagg.SAggregator;
import org.jlab.ersap.actor.sampa.source.recagg.SReceiverDecoder;
import org.jlab.ersap.actor.sampa.source.ring.SRingRawEvent;
import org.jlab.ersap.actor.sampa.source.ring.SRingRawEventFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.lmax.disruptor.RingBuffer.createSingleProducer;

/**
 * Copyright (c) 2021, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * @author gurjyan on 2/15/23
 * @project ersap-coda
 */
public class SReceiveDecodeAggregate extends Thread {

    // Number of active streams (FEC has 2 streams) depending
    // how many FEC we configure to read
    private int activeStreams;

    // Max ring items
    private final static int maxRingItems = 1024;

    // Aggregated RingBuffer for data transfer between aggregator and this object.
    private final RingBuffer<SRingRawEvent> aggRingBuffer;
    // Aggregated Ring sequence used to read data from aggregator.
    private final Sequence aggSequence;
    // Aggregated Ring barrier used to read data from aggregator.
    private final SequenceBarrier aggBarrier;

    // Data receivers receiving and decoding SAMPA streams
    private final SReceiverDecoder[] receivers;

    // Data aggregator reading from receivers and aggregating into the output ring buffer
    private final SAggregator aggregator;

    // Sequence of event to get next from last/aggregated ring.
    private long aggNextSequence;
    // Largest sequence of all events immediately available from last/aggregated ring.
    private long aggAvailableSequence;

    // Control for this thread termination.
    private volatile boolean running = true;

    // Pool of ByteBuffers of serialized SAMPA stream data.
    private final ConcurrentLinkedQueue<ByteBuffer> pool;

    public SReceiveDecodeAggregate(EMode eMode, ArrayList<Integer> activePorts) {
        activeStreams = activePorts.size();

        // Max number of frames to receive before ending program.
        int streamFrameLimit = 0;

        // Byte size of each buffer in each raw event (1 buf per channel)
        int byteSize = 8192;

        // RingBuffers in which receivers will get & fill events, then pass them to the aggregator
        receivers = new SReceiverDecoder[activeStreams];
        // RingBuffers in which receivers will get & fill events, then pass them to the aggregator
        RingBuffer<SRingRawEvent>[] ringBuffers = new RingBuffer[activeStreams];
        // Receiver ring sequences
        Sequence[] sequences = new Sequence[activeStreams];


        // Create receiver ring barriers
        SequenceBarrier[] barriers = new SequenceBarrier[activeStreams];

        for (int i = 0; i < activeStreams; i++) {
            ringBuffers[i] = createSingleProducer(
                    new SRingRawEventFactory(eMode, byteSize, false), maxRingItems,
                    new SpinCountBackoffWaitStrategy(30000, new LiteBlockingWaitStrategy()));

            // Ring sequence used by aggregator to read data from first receiver
            sequences[i] = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);

            // Ring barrier used by aggregator to read data from a receiver
            barriers[i] = ringBuffers[i].newBarrier();
            ringBuffers[i].addGatingSequences(sequences[i]);

            // Create the receiver
            receivers[i] = new SReceiverDecoder(activePorts.get(i),i,
                    ringBuffers[i], streamFrameLimit, eMode, byteSize);
        }
        // RingBuffer in which Aggregator will get empty events and fill them with data aggregated
        // from multiple streams. It then passes to this object which takes the place of the consumer.
        aggRingBuffer = createSingleProducer(
                new SRingRawEventFactory(eMode, byteSize, true), maxRingItems,
                new SpinCountBackoffWaitStrategy(30000, new LiteBlockingWaitStrategy()));

        aggSequence = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
        aggBarrier = aggRingBuffer.newBarrier();
        aggRingBuffer.addGatingSequences(aggSequence);

        // Create aggregator
        aggregator = new SAggregator(eMode, ringBuffers, sequences, barriers, aggRingBuffer);

        // Get this thread ready
        aggNextSequence = aggSequence.get() + 1L;
        aggAvailableSequence = -1L;

        // Create a pool of ByteBuffers
        pool = new ConcurrentLinkedQueue<>();
    }

    @Override
    public void run() {
        for (int i = 0; i < activeStreams; i++) {
            System.out.println("DDD starting stream receiver = " + i);
            receivers[i].start();
        }
        aggregator.start();
        while (running) {
            try {
                ByteBuffer b = getSerializedData();
                if (b != null) pool.add(b);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Get the next available item from output/aggregated ring buffer.
     * Do NOT call this multiple times in a row!
     * Be sure to call "put" before calling this again.
     *
     * @return next available item in ring buffer.
     * @throws InterruptedException if thread interrupted.
     */
    public SRingRawEvent get() throws InterruptedException {

        SRingRawEvent item = null;
        try {
            if (aggAvailableSequence < aggNextSequence) {
                aggAvailableSequence = aggBarrier.waitFor(aggNextSequence);
            }
            item = aggRingBuffer.get(aggNextSequence);
        } catch (final TimeoutException | AlertException ex) {
            // never happen since we don't use timeout wait strategy
            ex.printStackTrace();
        }
        return item;
    }

    /**
     * Release item claimed from aggregated ring buffer.
     */
    public void put() {
        // Tell input (crate) ring that we're done with the item we're consuming
        aggSequence.set(aggNextSequence);
        // Go to next item to consume on input ring
        aggNextSequence++;
    }

    /**
     * Get the next available item from output ring buffer.
     * Copy and serialize the item's data.
     * Release the item back to the ring.
     * Return serialized data.
     * This method does a get and put underneath.
     *
     * @return next available item in ring buffer.
     * @throws InterruptedException if thread interrupted.
     */
    private ByteBuffer getSerializedData() throws InterruptedException {

        SRingRawEvent item = get();

        // Serialize this data here. By doing the copy here,
        // the put() can be done immediately, greatly simplifying
        // the engine code to wrap this class.
        ByteBuffer bb = null;

        try {
            bb = DasDataType.serialize(item.getData());
        } catch (ErsapException e) {/* never happen */}

        put();

        return bb;
    }

    public ByteBuffer getEvent() {
        while (pool.isEmpty()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return pool.poll();
    }

    public void close() {
        for (int i = 0; i < activeStreams; i++) {
            receivers[i].exit();
        }
        aggregator.exit();
        running = false;
    }

}
