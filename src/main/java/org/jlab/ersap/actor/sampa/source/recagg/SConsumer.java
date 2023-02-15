package org.jlab.ersap.actor.sampa.source.recagg;

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
import org.jlab.ersap.actor.sampa.source.ring.SRingRawEvent;


/**
 * This class consumes events that have data which
 * has been aggregated from multiple input sources.
 */
public class SConsumer extends Thread {

    /** Ring buffer containing input events. */
    private final RingBuffer<SRingRawEvent> ringBuffer;
    /** Sequence of event taken from ring. */
    private final Sequence sequence;
    /** Ring barrier used to get events from ring. */
    private final SequenceBarrier barrier;
    /** Sequence of event to get next from ring. */
    private long nextSequence;
    /** Largest sequence of all events immediately available from ring. */
    private long availableSequence;

    /** Control for the thread termination. */
    private volatile boolean running = true;


    /**
     * Constructor.
     * @param ringBuffer ring buffer containing events that
     *                   have data aggregated from multiple inputs.
     * @param sequence   ring sequence for consuming events.
     * @param barrier    ring barrier for consuming events.
     */
    public SConsumer(RingBuffer<SRingRawEvent> ringBuffer,
                     Sequence sequence,
                     SequenceBarrier barrier) {

        this.ringBuffer = ringBuffer;
        this.sequence = sequence;
        this.barrier = barrier;

        nextSequence = sequence.get() + 1L;
        availableSequence = -1L;
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
                availableSequence = barrier.waitFor(nextSequence);
            }

            item = ringBuffer.get(nextSequence);
        } catch (final TimeoutException | AlertException ex) {
            // never happen since we don't use timeout wait strategy
            ex.printStackTrace();
        }

        return item;
    }


    /** Release item claimed from input ring buffer. */
    public void put() {
        // Tell input (crate) ring that we're done with the item we're consuming
        sequence.set(nextSequence);

        // Go to next item to consume on input ring
        nextSequence++;
    }


    /** Run this thread. */
    public void run() {

        boolean gotFirst = false;
        long evCount = 0L;

        while (running) {
            try {

                // Get an item from ring and parse the payload
                SRingRawEvent ev = get();
                evCount++;

                if (evCount % 1000 == 0) {
                    System.out.println("Consumer: event count = " + evCount);
                }

//                if (!gotFirst) {
//                    ev.printData(System.out, 0, true);
//                    gotFirst = true;
//                }

                put();

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    /** Stop this thread. */
    public void exit() {
        running = false;
        //this.interrupt();
    }

}
