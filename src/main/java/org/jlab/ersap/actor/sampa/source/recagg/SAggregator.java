package org.jlab.ersap.actor.sampa.source.recagg;

import com.lmax.disruptor.*;
import org.jlab.ersap.actor.sampa.EMode;
import org.jlab.ersap.actor.sampa.source.ring.SRingRawEvent;

import java.util.Arrays;

/**
 * Copyright (c) 2021, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * @author gurjyan on 2/14/23
 * @project ersap-coda
 *
 * Aggregates N SAMPA streams.
 */
public class SAggregator extends Thread {

    // SAMPA readout mode
    private final EMode EMode;
    // Input ring buffers
    private final RingBuffer<SRingRawEvent>[] ringBuffers;
    //Input ring sequences
    private final Sequence[] sequences;
    // Input ring barriers
    private final SequenceBarrier[] barriers;
    // Next sequences
    private long[] nextSequences;
    // Available sequences
    private long[] availableSequences;
    // Output RingBuffer
    private final RingBuffer<SRingRawEvent> outputRingBuffer;
    // Control for the thread termination
    private volatile boolean running = true;


    public SAggregator(EMode eMode, RingBuffer<SRingRawEvent>[] ringBuffers,
                       Sequence[] sequences, SequenceBarrier[] barriers,
                       RingBuffer<SRingRawEvent> outputRingBuffer)
             {

        System.out.println("DDDD ****** "+ ringBuffers.length +" "+
                sequences.length +" " +
                barriers.length);

        // Make sure the data is correct. Array sizes must be the same as the number of streams.
        if ((ringBuffers.length != sequences.length) ||
                (sequences.length != barriers.length)) {
            System.out.println("ERROR: Unequal array sizes.");
        }

        EMode = eMode;
        this.ringBuffers = ringBuffers;
        this.sequences = sequences;
        this.barriers = barriers;
        this.outputRingBuffer = outputRingBuffer;

        this.nextSequences = new long[ringBuffers.length];
        this.availableSequences = new long[ringBuffers.length];
        for (int i = 0; i < nextSequences.length; i++) {
            this.nextSequences[i] = this.sequences[i].get() + 1L;
            this.availableSequences[i] = -1L;
        }
    }

    /**
     * Get events from ring buffers
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
     */
    private SRingRawEvent[] getEvents()
            throws InterruptedException, AlertException, TimeoutException {

        SRingRawEvent[] events = new SRingRawEvent[ringBuffers.length];
        for (int i = 0; i < ringBuffers.length; i++) {
            if (availableSequences[i] < nextSequences[i]) {
                availableSequences[i] = barriers[i].waitFor(nextSequences[i]);
            }
            events[i] = ringBuffers[i].get(nextSequences[i]);
        }
        return events;
    }

    private void aggregateAndPublish(SRingRawEvent[] events)
            throws InterruptedException, AlertException, TimeoutException {

        int[] blockNumbers = new int[events.length];

        // Get block numbers from streams/ring buffers
        for (int i = 0; i < blockNumbers.length; i++) {
            blockNumbers[i] = events[i].getBlockNumber();
        }
        // Define the max block number
        int max = Arrays.stream(blockNumbers).max().getAsInt();

        if (EMode.isDSP()) {
            for (int i = 0; i < blockNumbers.length; i++) {
                while (blockNumbers[i] != max) {
                    // Dump block and read next from the ring buffer
                    sequences[i].set(nextSequences[i]++);
                }
            }
            // Get events from all ring buffers again. This time all sequences are aligned.
            events = getEvents();
        }

        // Get an event placeholder from the ring which holds the aggregated events
        long outSequence = outputRingBuffer.next();
        SRingRawEvent outputItem = outputRingBuffer.get(outSequence);
        outputItem.reset();

        // Set output ring item block number (ignored in DAS)
        outputItem.setBlockNumber(max);

        outputItem.setData(events[0].getData());
        for (int i = 1; i < events.length; i++) {
            outputItem.addData(events[i].getData());
        }
        // Publish
        outputRingBuffer.publish(outSequence);
    }

    /** Release items claimed from both input ring buffers. */
    private void put() {
        for (int i = 0; i < ringBuffers.length; i++) {
            sequences[i].set(nextSequences[i]);
            nextSequences[i]++;
        }
    }

    /** Run this thread. */
    public void run() {
        try {
            while (running) {
                getEvents();
                put();
            }
        } catch (InterruptedException | AlertException | TimeoutException e) {
            e.printStackTrace();
        }
    }

    /** Stop this thread. */
    public void exit() {
        running = false;
        //this.interrupt();
    }


}
