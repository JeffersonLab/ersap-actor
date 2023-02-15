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
import org.jlab.ersap.actor.sampa.source.ring.SRingRawEvent;

/**
 * This class implements an "aggregator" which combines 2 input data streams
 * into one output data stream. These streams are data coming from a SAMPA
 * board. This board can produce data in either the DSP or DAS format.
 * Both cases are handled.
 */
public class S2Aggregator extends Thread {

    /** Type of SAMPA data. */
    private final EMode EMode;

    /** RingBuffer for first input stream. */
    private final RingBuffer<SRingRawEvent> ringBuffer1;
    /** RingBuffer for second input stream. */
    private final RingBuffer<SRingRawEvent> ringBuffer2;
    /** Output RingBuffer. */
    private final RingBuffer<SRingRawEvent> outputRingBuffer;

    /** Consumer ring sequence for first input stream  */
    private final Sequence sequence1;
    /** Consumer ring sequence for second input stream  */
    private final Sequence sequence2;

    /** Ring barrier for consumer of first stream */
    private final SequenceBarrier barrier1;
    /** Ring barrier for consumer of second stream */
    private final SequenceBarrier barrier2;

    /** Sequence the aggregating consumer wants next from first input ring. */
    private long nextSequence1;
    /** Sequence the aggregating consumer wants next from second input ring. */
    private long nextSequence2;

    /** Sequence currently available from first input ring. */
    private long availableSequence1;
    /** Sequence currently available from second input ring. */
    private long availableSequence2;

    /** Control for the thread termination. */
    private volatile boolean running = true;


    /**
     * Constructor.
     * @param ringBuffer1       ring buffer containing events from input data stream 1.
     * @param ringBuffer2       ring buffer containing events from input data stream 2.
     * @param sequence1         consumer ring sequence for input stream 1.
     * @param sequence2         consumer ring sequence for input stream 2.
     * @param barrier1          ring barrier for consumer of stream 1.
     * @param barrier2          ring barrier for consumer of stream 2.
     * @param outputRingBuffer  ring buffer containing events from the output data stream.
     * @param EMode         type of data coming from the input streams.
     */
    public S2Aggregator(RingBuffer<SRingRawEvent> ringBuffer1,
                        RingBuffer<SRingRawEvent> ringBuffer2,
                        Sequence sequence1, Sequence sequence2,
                        SequenceBarrier barrier1, SequenceBarrier barrier2,
                        RingBuffer<SRingRawEvent> outputRingBuffer,
                        EMode EMode) {

        this.ringBuffer1 = ringBuffer1;
        this.ringBuffer2 = ringBuffer2;
        this.sequence1 = sequence1;
        this.sequence2 = sequence2;
        this.barrier1 = barrier1;
        this.barrier2 = barrier2;
        this.outputRingBuffer = outputRingBuffer;
        this.EMode = EMode;

        nextSequence1 = sequence1.get() + 1L;
        nextSequence2 = sequence2.get() + 1L;

        availableSequence1 = -1L;
        availableSequence2 = -1L;
    }


    /**
     * Get 2 events, one from each of the 2 input streams, aggregate the data
     * and place it into a 3rd event from the output stream.
     * @throws InterruptedException if thread interrupted.
     */
    private void get() throws InterruptedException {

        try {
            SRingRawEvent inputItem1 = getEventStream1();
            SRingRawEvent inputItem2 = getEventStream2();
            aggregateAndPublish(inputItem1, inputItem2);

        } catch (final TimeoutException | AlertException ex) {
            // This will never happen given the wait strategy used
            // (see SMPTwoStreamAggregatorDecoder)
            ex.printStackTrace();
        }
    }

    /**
     * Get an event from stream 1.
     *
     * @return event from stream 1.
     * @throws InterruptedException if thread interrupted.
     * @throws TimeoutException never thrown.
     * @throws AlertException never thrown.
     */
    private SRingRawEvent getEventStream1() throws InterruptedException,
            TimeoutException,
            AlertException {
        if (availableSequence1 < nextSequence1) {
            availableSequence1 = barrier1.waitFor(nextSequence1);
        }
        return ringBuffer1.get(nextSequence1);
    }


    /**
     * Get an event from stream 2.
     *
     * @return event from stream 2.
     * @throws InterruptedException if thread interrupted.
     * @throws TimeoutException never thrown.
     * @throws AlertException never thrown.
     */
    private SRingRawEvent getEventStream2() throws InterruptedException,
            TimeoutException,
            AlertException {
        if (availableSequence2 < nextSequence2) {
            availableSequence2 = barrier2.waitFor(nextSequence2);
        }
        return ringBuffer2.get(nextSequence2);
    }


    /**
     * <p>Combine the data of 2 events (one from each of 2 receiving ring buffers)
     * into one event in a third ring buffer.</p>
     *
     * <p>DSP mode: The assumption is made that on each input stream, blocks arrive in
     * numerical order with the block number increasing by one in each successive event.
     * It may be possible that particular members of that sequence are missing, but it
     * will still be monotonically increasing. Based on that assumption, the logic matches
     * up data with identical block numbers from each of the 2 input streams and saves
     * that together in one event.</p>
     *
     * DAS mode: In this mode, there is no reliable clock accompanying the data. It relies
     * entirely on the sync signal and on not dropping any data in the receiver in assuring
     * that the given events are from the same time and can be combined.
     *
     * @param e1 event from input ring buffer 1.
     * @param e2 event from input ring buffer 2.
     *
     * @throws InterruptedException if thread interrupted.
     * @throws TimeoutException if any operation on one of the 3 ring buffers times out.
     *                          This will never happen given the current wait strategy used.
     * @throws AlertException this exception should never happen.
     */
    private void aggregateAndPublish(SRingRawEvent e1, SRingRawEvent e2) throws InterruptedException,
            TimeoutException,
            AlertException {
        int b1 = e1.getBlockNumber();
        int b2 = e2.getBlockNumber();

        if (EMode.isDSP()) {
            // While blocks don't match up
            while (b1 != b2) {
                // If stream 2 lost one or more blocks ...
                if (b1 < b2) {
                    // Dump block and read next for receiver 1
                    sequence1.set(nextSequence1++);
                    e1 = getEventStream1();
                    b1 = e1.getBlockNumber();
                }
                // If stream 1 lost one or more blocks ...
                else {
                    // Dump block and read next for receiver 2
                    sequence2.set(nextSequence2++);
                    e2 = getEventStream2();
                    b2 = e2.getBlockNumber();
                }
            }
        }

        // If we're here, in DSP mode, b1 == b2 and everything is fine as both events match up

        // Get an event from the ring which holds the aggregated events
        long outSequence = outputRingBuffer.next();
        SRingRawEvent outputItem = outputRingBuffer.get(outSequence);
        outputItem.reset();

        // Set output ring item block number (ignored in DAS)
        outputItem.setBlockNumber(b1);

        // Add two data arrays together and add to the output item
//        ByteBuffer[] data1 = e1.getData();
//        int numShorts = data1[0].position()/2;
//
//        System.out.println("AGGREGATOR:");
//        for (int i = 0; i < numShorts; i++) {
//            short s = data1[0].getShort(i*2);
//            System.out.print(" " + Integer.toHexString(s));
//            if ((i+1) % 10 == 0) System.out.println();
//        }
//        System.out.println();
//
//        ByteBuffer[] data2 = e2.getData();
//        numShorts = data2[0].position()/2;
//        System.out.println();
//        for (int i = 0; i < numShorts; i++) {
//            short s = data2[0].getShort(i*2);
//            System.out.print(" " + Integer.toHexString(s));
//            if ((i+1) % 10 == 0) System.out.println();
//        }
//        System.out.println();

        outputItem.setData(e1.getData());
        outputItem.addData(e2.getData());

        // Publish
        outputRingBuffer.publish(outSequence);
    }


    /** Release items claimed from both input ring buffers. */
    private void put() {
        sequence1.set(nextSequence1);
        nextSequence1++;

        sequence2.set(nextSequence2);
        nextSequence2++;
    }

    /** Run this thread. */
    public void run() {
        try {
            while (running) {
                get();
                put();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /** Stop this thread. */
    public void exit() {
        running = false;
        //this.interrupt();
    }

}
