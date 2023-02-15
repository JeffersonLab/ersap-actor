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
import org.jlab.ersap.actor.sampa.EMode;
import org.jlab.ersap.actor.sampa.source.recagg.SConsumer;
import org.jlab.ersap.actor.sampa.source.recagg.SReceiverDecoder;
import org.jlab.ersap.actor.sampa.source.ring.SRingRawEvent;
import org.jlab.ersap.actor.sampa.source.ring.SRingRawEventFactory;

import java.io.IOException;
import static com.lmax.disruptor.RingBuffer.createSingleProducer;
import static org.jlab.ersap.actor.sampa.EMode.DAS;
import static org.jlab.ersap.actor.sampa.EMode.DSP;


public class S2RecDecAggCons {

    /** TCP Port for receiving data from first stream. */
    private final int sampaPort1;
    /** TCP Port for receiving data from second stream. */
    private final int sampaPort2;
    /** Stream id of first stream. */
    private final int streamId1;
    /** Stream id of second stream. */
    private final int streamId2;
    /** Max number of frames to receive before ending program. */
    private final int streamFrameLimit;

    /** Format of data SAMPA chips are sending. */
    private final EMode EMode;

    /** Max ring items */
    private final static int maxRingItems = 16;

    /** Size in bytes of each buffer in a raw event (1 buf per channel). */
    private final int byteSize;


    /** Ring buffer for data transfer between receiver 1 and aggregator. */
    private final RingBuffer<SRingRawEvent> ringBuffer1;
    /** Ring buffer for data transfer between receiver 2 and aggregator. */
    private final RingBuffer<SRingRawEvent> ringBuffer2;
    /** Ring buffer for data transfer between aggregator and consumer. */
    private final RingBuffer<SRingRawEvent> ringBuffer12;


    /** Ring sequence used by aggregator to read data from first receiver. */
    private final Sequence sequence1;
    /** Ring sequence used by aggregator to read data from second receiver. */
    private final Sequence sequence2;
    /** Ring sequence used by consumer to read data from aggregator. */
    private final Sequence sequence12;


    /** Ring barrier used by aggregator to read data from first receiver. */
    private final SequenceBarrier sequenceBarrier1;
    /** Ring barrier used by aggregator to read data from second receiver. */
    private final SequenceBarrier sequenceBarrier2;
    /** Ring barrier used by consumer to read data from aggregator. */
    private final SequenceBarrier sequenceBarrier12;

    /** Data receiver reading from first data stream. */
    private SReceiverDecoder receiver1;
    /** Data receiver reading from second data stream. */
    private SReceiverDecoder receiver2;
    /** Data aggregator reading from both receivers. */
    private S2Aggregator aggregator12;
    /** Data consumer reading from aggregator. */
    private SConsumer consumer;


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
    public S2RecDecAggCons(int sampaPort1, int sampaPort2,
                           int streamId1, int streamId2,
                           int streamFrameLimit,
                           EMode EMode) {
        this.sampaPort1 = sampaPort1;
        this.sampaPort2 = sampaPort2;
        this.streamId1 = streamId1;
        this.streamId2 = streamId2;
        this.streamFrameLimit = streamFrameLimit;
        this.EMode = EMode;

        // Byte size of each buffer in each raw event
        byteSize = 8192;

        // RingBuffer in which receiver1 will get & fill events, then pass them to the aggregator
        ringBuffer1 = createSingleProducer(new SRingRawEventFactory(EMode, byteSize, false), maxRingItems,
                new SpinCountBackoffWaitStrategy(30000, new LiteBlockingWaitStrategy()));

        sequence1 = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
        sequenceBarrier1 = ringBuffer1.newBarrier();
        ringBuffer1.addGatingSequences(sequence1);

        // RingBuffer in which receiver2 will get & fill events, then pass them to the aggregator
        ringBuffer2 = createSingleProducer(new SRingRawEventFactory(EMode, byteSize, false), maxRingItems,
                new SpinCountBackoffWaitStrategy(30000, new LiteBlockingWaitStrategy()));

        sequence2 = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
        sequenceBarrier2 = ringBuffer2.newBarrier();
        ringBuffer2.addGatingSequences(sequence2);

        // RingBuffer in which Aggregator will get empty events and fill them with data aggregated
        // from the 2 streams. It then passes them to the consumer.
        ringBuffer12 = createSingleProducer(new SRingRawEventFactory(EMode, byteSize, true), maxRingItems,
                new SpinCountBackoffWaitStrategy(30000, new LiteBlockingWaitStrategy()));

        sequence12 = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
        sequenceBarrier12 = ringBuffer12.newBarrier();
        ringBuffer12.addGatingSequences(sequence12);

    }

    public void go() throws IOException {
        receiver1 = new SReceiverDecoder(sampaPort1, streamId1, ringBuffer1, streamFrameLimit, EMode, byteSize);
        receiver2 = new SReceiverDecoder(sampaPort2, streamId2, ringBuffer2, streamFrameLimit, EMode, byteSize);

        aggregator12 = new S2Aggregator(ringBuffer1, ringBuffer2,
                sequence1, sequence2,
                sequenceBarrier1, sequenceBarrier2,
                ringBuffer12, EMode);

        consumer = new SConsumer(ringBuffer12, sequence12, sequenceBarrier12);
        receiver1.start();
        receiver2.start();

        aggregator12.start();
        consumer.start();
    }

    public void close() {
        receiver1.exit();
        receiver2.exit();
        aggregator12.exit();
        consumer.exit();
    }

    /**
     * Main method. Arguments are:
     * <ol>
     * <li>port of TCP server to run in first SReceiver object
     * <li>port of TCP server to run in second SReceiver object
     * <li>id of first SReceiver's data stream
     * <li>id of second SReceiver's data stream
     * <li>limit on number of frames to parse on each stream
     * <li>optional: if = DAS, it switches from parsing DSP format to DAS format data
     * </ol>
     * @param args array of args.
     */
    public static void main(String[] args) {
        int port1 = Integer.parseInt(args[0]);
        int port2 = Integer.parseInt(args[1]);

        int streamId1 = Integer.parseInt(args[2]);
        int streamId2 = Integer.parseInt(args[3]);

        int streamFrameLimit = Integer.parseInt(args[4]);

        EMode sampaType = DSP;
        if (args.length > 5) {
            String sType = args[5];
            if (sType.equalsIgnoreCase("das")) {
                sampaType = DAS;
            }
        }

        try {
            new S2RecDecAggCons(port1, port2, streamId1, streamId2,
                    streamFrameLimit, sampaType).go();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
