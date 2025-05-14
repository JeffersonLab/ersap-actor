package org.jlab.ersap.actor.coda.source.et;

import org.jlab.coda.et.*;
import org.jlab.coda.et.enums.Mode;
import org.jlab.coda.et.enums.Modify;
import org.jlab.ersap.actor.coda.proc.fadc.FadcUtil;
import org.jlab.ersap.actor.util.IASource;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.lmax.disruptor.RingBuffer.createSingleProducer;
import static org.jlab.ersap.actor.util.AUtil.copyBytes;

/**
 * Copyright (c) 2021, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * @author gurjyan and timmer on 6/6/24
 * {@code} ersap-coda
 * <p>
 * <p>
 * Establishes a connection to the ET running on a local host.
 * That is, one ET for each node that is responsible for running the ERSAP program.
 */
public class CodaETReader implements IASource, Runnable {

    private EtSystem etSystem;
    EtAttachment etAttachment;
    // Number of ET buffers to get from the ET at once.
    int chunk = 10;
    // array of events
    private EtEvent[] etEvents;
    private int evtCount =0;

    // Variables for statistics
    private long t1 = 0L;
    private long totalT = 0L;
    private long count = 0L;
    private long totalCount = 0L;
    private long bytes = 0L;
    private long totalBytes = 0L;

    // Queue
    private final BlockingQueue<ByteBuffer> queue;

    private AtomicBoolean running = new AtomicBoolean(true);

    public CodaETReader(String etName, int etPort, String etStationName, int capacity) {
        // Queue staff
        this.queue = new LinkedBlockingQueue<>(capacity);

        // ET staff
        EtSystemOpenConfig config = new EtSystemOpenConfig();
        System.out.println("Connecting to local "+etName+" ET system.");
        try {
            config.setNetworkContactMethod(EtConstants.direct);
            config.setHost(EtConstants.hostLocal);
            config.setTcpPort(etPort); // e.g. 23911
            config.setWaitTime(0);
            config.setEtName(etName);
            // create ET system object with verbose debugging output
            etSystem = new EtSystem(config);
            etSystem.open();
            System.out.println("Connected to ET.");

            // Create station after all other stations
            EtStationConfig statConfig = new EtStationConfig();
            EtStation station = etSystem.createStation(statConfig, etStationName, EtConstants.end, EtConstants.end);

            // Attach to new station
            etAttachment = etSystem.attach(station);
            System.out.println("Created and attached to the "+ station+" station.");
            // keep track of time
            t1 = System.currentTimeMillis();
        } catch (Exception ex) {
            System.out.println("Error using ET system as consumer");
            ex.printStackTrace();
        }

        // Starting ET consumer thread
        Thread thread = new Thread(this);
        thread.start();
    }

    /**
     * Gets a single event from an ET entry buffer
     *
     * @return event as a ByteBuffer
     */
    private ByteBuffer getEtEntryBuffer() {

        // Get event's data buffer
        ByteBuffer buf = etEvents[evtCount].getDataBuffer();
        // Data length in bytes
        // Data length from ET in bytes
        int dataLength = etEvents[evtCount].getLength();
        bytes += dataLength;
        totalBytes += dataLength;

        // Increment ET entry buffer event count
        evtCount++;

        return buf;
    }


    public byte[] nextEtEvent() {
        ByteBuffer bufo;
        try {
            if ((etEvents == null) || (evtCount == etEvents.length)) {

                // Put events back into ET system if we're done with all chunk of them
                if (evtCount > 0 && etEvents != null) {
                    etSystem.putEvents(etAttachment, etEvents);
                    count += evtCount;
                }

                // Get chunk more events (ET buffer) from ET system
                etEvents = etSystem.getEvents(etAttachment, Mode.SLEEP, Modify.ANYTHING, 0, chunk);
                evtCount = 0;
            }

            // Get a single event from the ET entry buffer
            bufo = getEtEntryBuffer();
            assert bufo != null;
            return copyBytes(bufo.array());
        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new RuntimeException();
        }
    }
   public ByteBuffer nextEtBuffer() {
        try {
            if ((etEvents == null) || (evtCount == etEvents.length)) {

                // Put events back into ET system if we're done with all chunk of them
                if (evtCount > 0 && etEvents != null) {
                    etSystem.putEvents(etAttachment, etEvents);
                    count += evtCount;
                }

                // Get chunk more events (ET buffer) from ET system
                etEvents = etSystem.getEvents(etAttachment, Mode.SLEEP, Modify.ANYTHING, 0, chunk);
                evtCount = 0;
            }

            // Get a single event from the ET entry buffer
            return  getEtEntryBuffer();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new RuntimeException();
        }
    }

    @Override
    public Object nextEvent() {
        try {
             return dequeue();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getEventCount() {
        return Integer.MAX_VALUE;
    }

    @Override
    public ByteOrder getByteOrder() {
        return FadcUtil.evioDataByteOrder;
    }

    @Override
    public void close() {
        etSystem.close();
        running.set(false);
    }
    public void enqueue(ByteBuffer buffer) throws InterruptedException {
        queue.put(buffer); // Blocks if the queue is full
    }

    public ByteBuffer dequeue() throws InterruptedException {
        return queue.take(); // Blocks if the queue is empty
    }


    private void printStatistics() {
        // calculate the event rate
        long t2 = System.currentTimeMillis();
        long time = t2 - t1;

        if (time > 5000) {
            // reset things if necessary
            if ((totalCount >= (Long.MAX_VALUE - count)) ||
                    (totalT >= (Long.MAX_VALUE - time))) {
                bytes = totalBytes = totalT = totalCount = count = 0L;
                t1 = t2;
                return;
            }

            double rate = 1000.0 * ((double) count) / time;
            totalCount += count;
            totalT += time;
            double avgRate = 1000.0 * ((double) totalCount) / totalT;
            // Event rates
            System.out.println("Events = " + String.format("%.3g", rate) +
                    " Hz,    avg = " + String.format("%.3g", avgRate));

            // Data rates
            rate = ((double) bytes) / time;
            avgRate = ((double) totalBytes) / totalT;
            System.out.println("  Data = " + String.format("%.3g", rate) +
                    " kB/s,  avg = " + String.format("%.3g", avgRate) + "\n");

            bytes = count = 0L;
            t1 = System.currentTimeMillis();
        }
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                enqueue(nextEtBuffer());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }


        }
    }
}
