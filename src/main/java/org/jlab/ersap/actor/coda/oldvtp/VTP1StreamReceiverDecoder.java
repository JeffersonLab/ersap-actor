package org.jlab.ersap.actor.coda.oldvtp;

import com.lmax.disruptor.*;
import sun.misc.Signal;

import java.nio.ByteBuffer;

import static com.lmax.disruptor.RingBuffer.createSingleProducer;

/**
 * Copyright (c) 2021, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * @author gurjyan on 2/23/23
 * @project ersap-coda
 */
public class VTP1StreamReceiverDecoder {

    /**
     * VTP port
     */
    private int vtpPort1;

    /**
     * Max ring items
     */
    private final static int maxRingItems = 32768;

    /**
     * Ring buffer
     */
    private RingBuffer<VRingRawEvent> ringBuffer1;

    /**
     * Sequences
     */
    private Sequence sequence1;

    /**
     * Sequence barriers
     */
    private SequenceBarrier sequenceBarrier1;

    private VReceiver receiver1;
    private VConsumer consumer;

    private boolean started = false;

    public static Fadc2Hipo hipoFile;
    public static int ebEvents;

    public VTP1StreamReceiverDecoder(int vtpPort1, String fileName) {
        this.vtpPort1 = vtpPort1;

        ringBuffer1 = createSingleProducer(new VRingRawEventFactory(), maxRingItems,
                new YieldingWaitStrategy());
        sequence1 = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
        sequenceBarrier1 = ringBuffer1.newBarrier();
        ringBuffer1.addGatingSequences(sequence1);

        hipoFile = new Fadc2Hipo(fileName);
    }

    public void go() {
        if(!started) {
            receiver1 = new VReceiver(vtpPort1, 1, ringBuffer1, 10);
            int runNumber = 0;
            consumer = new VConsumer(ringBuffer1, sequence1, sequenceBarrier1, runNumber);

            receiver1.start();
            consumer.start();
            started = true;
        }
    }

    public ByteBuffer getDecodedEvent() throws Exception {
        return consumer.getEvent();
    }

    public void close(){
        receiver1.exit();
        consumer.exit();
        started = false;
    }

    public static void main(String[] args) {
        int port1 = Integer.parseInt(args[0]);
        new VTP1StreamReceiverDecoder(port1, args[1]).go();
        Signal.handle(new Signal("INT"),  // SIGINT
                signal -> {
                    System.out.println("Interrupted by Ctrl+C");
                    VTP1StreamReceiverDecoder.hipoFile.close();
                    System.exit(0);
                });
    }

}
