package org.jlab.ersap.actor.sampa.source.s2;

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
import com.lmax.disruptor.*;
import org.jlab.epsci.ersap.base.error.ErsapException;
import org.jlab.ersap.actor.datatypes.DasDataType;
import org.jlab.ersap.actor.sampa.EMode;
import org.jlab.ersap.actor.sampa.source.recagg.SReceiverDecoder;
import org.jlab.ersap.actor.sampa.source.ring.SRingRawEvent;
import org.jlab.ersap.actor.sampa.source.ring.SRingRawEventFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.lmax.disruptor.RingBuffer.createSingleProducer;


/**
 * This class is the SMPTwoStreamAggregatorDecoder except that this class
 * replaces the Consumer. This class is wrapped up as an engine to
 * provide SAMPA data.
 *
 * @author timmer
 */
public class S2RecDecAgg extends Thread {

    /** Max number of frames to receive before ending program. */
    private final int streamFrameLimit;

    /** Max ring items */
    private final static int maxRingItems = 2048;


    /** Ring buffer for data transfer between aggregator and this object. */
    private final RingBuffer<SRingRawEvent> ringBuffer12;
    /** Ring sequence used to read data from aggregator. */
    private final Sequence sequence12;
    /** Ring barrier used to read data from aggregator. */
    private final SequenceBarrier barrier12;


    /** Data receiver reading from first data stream. */
    private final SReceiverDecoder receiver1;
    /** Data receiver reading from second data stream. */
    private final SReceiverDecoder receiver2;
    /** Data aggregator reading from both receivers. */
    private final S2Aggregator aggregator12;


    /** Sequence of event to get next from last ring. */
    private long nextSequence;
    /** Largest sequence of all events immediately available from last ring. */
    private long availableSequence;

    /** Control for the go method termination. */
    private volatile boolean running = true;

    /** Pool of ByteBuffers of serialized sampa stream data */
    private ConcurrentLinkedQueue<ByteBuffer> pool;

    /**
     * Constructor.
     *
     * @param sampaPort1       TCP port for first  data producer to connect to.
     * @param sampaPort2       TCP port for second data producer to connect to.
     * @param streamId1        id number of first  data stream / producer.
     * @param streamId2        id number of second data stream / producer.
     * @param streamFrameLimit total number of frames consumed before exiting.
     * @param EMode        type of data coming over TCP sockets.
     */
    public S2RecDecAgg(int sampaPort1, int sampaPort2,
                       int streamId1, int streamId2,
                       int streamFrameLimit,
                       EMode EMode) {

        this.streamFrameLimit = streamFrameLimit;


        // Byte size of each buffer in each raw event (1 buf per channel)
        int byteSize = 8192;

        // RingBuffer in which receiver1 will get & fill events, then pass them to the aggregator
        RingBuffer<SRingRawEvent> ringBuffer1 = createSingleProducer(new SRingRawEventFactory(EMode, byteSize, false), maxRingItems,
                new SpinCountBackoffWaitStrategy(30000, new LiteBlockingWaitStrategy()));
//        new YieldingWaitStrategy());

        // Ring sequence used by aggregator to read data from first receiver
        Sequence sequence1 = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
        // Ring barrier used by aggregator to read data from first receiver
        SequenceBarrier barrier1 = ringBuffer1.newBarrier();
        ringBuffer1.addGatingSequences(sequence1);

        // RingBuffer in which receiver2 will get & fill events, then pass them to the aggregator
        RingBuffer<SRingRawEvent> ringBuffer2 = createSingleProducer(new SRingRawEventFactory(EMode, byteSize, false), maxRingItems,
                new SpinCountBackoffWaitStrategy(30000, new LiteBlockingWaitStrategy()));
//        new YieldingWaitStrategy());

        // Ring sequence used by aggregator to read data from second receiver
        Sequence sequence2 = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
        // Ring barrier used by aggregator to read data from second receiver
        SequenceBarrier barrier2 = ringBuffer2.newBarrier();
        ringBuffer2.addGatingSequences(sequence2);

        // RingBuffer in which Aggregator will get empty events and fill them with data aggregated
        // from the 2 streams. It then passes to this object which takes the place of the consumer.
        ringBuffer12 = createSingleProducer(new SRingRawEventFactory(EMode, byteSize, true), maxRingItems,
                new SpinCountBackoffWaitStrategy(30000, new LiteBlockingWaitStrategy()));
//        new YieldingWaitStrategy());

        sequence12 = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
        barrier12 = ringBuffer12.newBarrier();
        ringBuffer12.addGatingSequences(sequence12);


        // Create the receivers and the aggregator threads
        receiver1 = new SReceiverDecoder(sampaPort1, streamId1, ringBuffer1, streamFrameLimit, EMode, byteSize);
        receiver2 = new SReceiverDecoder(sampaPort2, streamId2, ringBuffer2, streamFrameLimit, EMode, byteSize);

        aggregator12 = new S2Aggregator(ringBuffer1, ringBuffer2,
                sequence1, sequence2,
                barrier1, barrier2,
                ringBuffer12, EMode);


        // Get this thread ready
        nextSequence = sequence12.get() + 1L;
        availableSequence = -1L;

        // Create a pool of ByteBuffers
        pool = new ConcurrentLinkedQueue<>();
    }



    @Override
    public void run() {

        receiver1.start();
        receiver2.start();
        aggregator12.start();
        while (running) {
            try {
                ByteBuffer b = getSerializedData();
                if(b!=null) pool.add(b);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    public void close() {
        receiver1.exit();
        receiver2.exit();
        aggregator12.exit();
    }

    /**
     * Get the next available item from output ring buffer.
     * Do NOT call this multiple times in a row!
     * Be sure to call "put" before calling this again.
     *
     * @return next available item in ring buffer.
     * @throws InterruptedException if thread interrupted.
     */
    public SRingRawEvent get() throws InterruptedException {

        SRingRawEvent item = null;

        try {
            if (availableSequence < nextSequence) {
                availableSequence = barrier12.waitFor(nextSequence);
            }

            item = ringBuffer12.get(nextSequence);
        } catch (final TimeoutException | AlertException ex) {
            // never happen since we don't use timeout wait strategy
            ex.printStackTrace();
        }

        return item;
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
        while (pool.isEmpty()){
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return pool.poll();
    }

    /** Release item claimed from input ring buffer. */
    public void put() {
        // Tell input (crate) ring that we're done with the item we're consuming
        sequence12.set(nextSequence);

        // Go to next item to consume on input ring
        nextSequence++;
    }



    //    public static void main(String[] args) {
//        int port1 = Integer.parseInt(args[0]);
//        int port2 = Integer.parseInt(args[1]);
//
//        SMPTwoStreamEngineAggregator s = new SMPTwoStreamEngineAggregator(port1, port2,
//                1, 2,
//                0, SampaType.DAS);
//        s.go();
//        int evCount = 0;
//
//
//        while (true) {
//            try {
//                evCount++;
//                if (evCount % 1000 == 0) {
//                    System.out.println("Consumer: event count = " + evCount);
//                }
//                s.getSerializedData();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
//
//        }


}
