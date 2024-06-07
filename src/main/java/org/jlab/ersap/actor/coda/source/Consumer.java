package org.jlab.ersap.actor.coda.source;

import com.lmax.disruptor.*;

import java.nio.ByteBuffer;

public class Consumer {
    private RingBuffer<RingEvent> ringBuffer;
    private Sequence sequence;
    private SequenceBarrier barrier;
    private long nextSequence;
    private long availableSequence;

    public Consumer(RingBuffer<RingEvent> ringBuffer,
                    Sequence sequence,
                    SequenceBarrier barrier) {
        this.ringBuffer = ringBuffer;
        this.sequence = sequence;
        this.barrier = barrier;

        nextSequence = sequence.get() + 1L;
        availableSequence = -1L;
    }

    public RingEvent get() throws InterruptedException {
        RingEvent event = null;
        try {
            if (availableSequence < nextSequence) {
                availableSequence = barrier.waitFor(nextSequence);
            }

            event = ringBuffer.get(nextSequence);
        } catch (final TimeoutException | AlertException ex) {
            ex.printStackTrace();
        }
        return event;
    }

    public void put() throws InterruptedException {

        // Tell input ring that we're done with the event we're consuming
        sequence.set(nextSequence);

        // Go to next item to consume on input ring
        nextSequence++;
    }

    public ByteBuffer getEvent() throws Exception {
        RingEvent event = get();
        ByteBuffer b = event.getPayloadBuffer();
        System.out.println("DDD : " + b.array().length);
        put();
        return b;
    }

    public void exit() {
    }
}

