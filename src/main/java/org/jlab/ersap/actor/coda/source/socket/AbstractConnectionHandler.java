package org.jlab.ersap.actor.coda.source.socket;

import com.lmax.disruptor.*;

import java.nio.ByteOrder;

/**
 * Abstract class representing a connection handler for a resource or source.
 * Subclasses must implement the specific details for establishing connections
 * and handling data. This class uses the LMAX Disruptor library to process events.
 */
public abstract class AbstractConnectionHandler {

    protected final RingBuffer<Event> disruptorRingBuffer;
    private final Sequence sequence; // Tracks the consumer's progress
    private final SequenceBarrier barrier; // Ensures thread-safe access to the RingBuffer

    /**
     * Constructor to initialize the disruptor ring buffer.
     *
     * @param disruptorRingBuffer the RingBuffer instance for managing events.
     */
    protected AbstractConnectionHandler(RingBuffer<Event> disruptorRingBuffer) {
        this.disruptorRingBuffer = disruptorRingBuffer;
        this.barrier = disruptorRingBuffer.newBarrier(); // Create a SequenceBarrier for safe access
        this.sequence = new Sequence(RingBuffer.INITIAL_CURSOR_VALUE); // Initialize consumer sequence
        disruptorRingBuffer.addGatingSequences(sequence); // Register this sequence for gating
    }

    /**
     * Retrieves the next event from the RingBuffer in a thread-safe manner.
     *
     * @return the next Event object or null if no event is available.
     */
    public Event getNextEvent() {
        try {
            // Retrieve the next available sequence for this consumer
            long nextSequence = sequence.get() + 1;

            // Wait until the sequence is available
            long availableSequence = barrier.waitFor(nextSequence);

            if (nextSequence <= availableSequence) {
                // Retrieve the event from the RingBuffer
                Event event = disruptorRingBuffer.get(nextSequence);

                // Update the consumer sequence after processing
                sequence.set(nextSequence);

                return event;
            }
        } catch (TimeoutException e) {
            System.err.println("Timeout while waiting for the next event.");
        } catch (AlertException e) {
            System.err.println("Alert received while waiting for the next event.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Interrupted while waiting for the next event.");
        }

        return null; // No event available or an error occurred
    }

    /**
     * Abstract methods to be implemented by subclasses.
     */
    public abstract Object establishConnection();

    public abstract void listenAndPublish(Object connection);

    public abstract ByteOrder getByteOrder();

    public abstract void closeConnection(Object connection);

    protected abstract byte[] receiveData(Object connection);
}