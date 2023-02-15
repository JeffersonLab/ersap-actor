package org.jlab.ersap.actor.coda.source.et;

import org.jlab.coda.et.*;
import org.jlab.coda.et.exception.EtClosedException;
import org.jlab.coda.et.exception.EtDeadException;
import org.jlab.coda.et.exception.EtException;
import org.jlab.coda.et.exception.EtWakeUpException;
import org.jlab.ersap.actor.util.ISourceReader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Copyright (c) 2021, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * @author gurjyan on 2/13/23
 * @project ersap-coda
 * <p>
 * <p>
 * Establishes a connection to the ET running on a local host.
 * That is, one ET for each node that is responsible for running the ERSAP program.
 */
public class CodaETReader implements ISourceReader {

    private EtSystem sys;
    private EtFifo fifo;
    private EtFifoEntry entry;
    private int entryCap;
    private int idCount;

    // array of events
    private EtEvent[] mevs;

    private int len, bufId;
    private long t1 = 0L, t2 = 0L, time, totalT = 0L, count = 0L, totalCount = 0L, bytes = 0L, totalBytes = 0L;
    private double rate, avgRate;

    public CodaETReader(String etName) {
        EtSystemOpenConfig config = new EtSystemOpenConfig();
        try {
            config.setNetworkContactMethod(EtConstants.direct);
            config.setHost(EtConstants.hostLocal);
            config.setWaitTime(0);
            config.setEtName(etName);
            // create ET system object with verbose debugging output
            sys = new EtSystem(config);
            sys.open();
            System.out.println("Connect to local ET");

            fifo = new EtFifo(sys);

            entry = new EtFifoEntry(sys, fifo);

            entryCap = fifo.getEntryCapacity();

            // keep track of time
            t1 = System.currentTimeMillis();
        } catch (Exception ex) {
            System.out.println("Error using ET system as consumer");
            ex.printStackTrace();
        }
    }

    @Override
    public Object nextEvent() {
        ByteBuffer buf = null;
        // get events from ET system
        try {
            fifo.getEntry(entry);
        mevs = entry.getBuffers();

        idCount = 0;

        // reading event data, iterate thru each event in a single fifo entry (1 in this case)
        for (int i = 0; i < entryCap; i++) {
            // Does this buffer have any data? (Set by producer)
            if (!mevs[i].hasFifoData()) {
                // Once we hit a buffer with no data, there is no further data
                break;
            }
            idCount++;

            // Id associated with this buffer in this fifo entry.
            // Not useful if only 1 buffer in each fifo entry as is the case here.
            bufId = mevs[i].getFifoId();

            // Get event's data buffer
            buf = mevs[i].getDataBuffer();
            // Data length in bytes
            len = mevs[i].getLength();
            bytes += len;
            totalBytes += len;

            // put events back into ET system
                fifo.putEntry(entry);

            count += idCount;
        }
        } catch (EtException | EtDeadException | EtClosedException | EtWakeUpException | IOException e) {
            e.printStackTrace();
        }

        // @todo How to convert byteBuffer into EvioEvent before returning?
        return buf;
    }

    @Override
    public int getEventCount() {
        return -1; // unbounded/streaming
    }

    @Override
    public ByteOrder getByteOrder() {
        // @todo how to get byte ordering from the ET entry?
        return null;
    }

    @Override
    public void close() {
        fifo.close();
        sys.close();
    }

    private void printStatistics(){
        // calculate the event rate
        t2 = System.currentTimeMillis();
        time = t2 - t1;

        if (time > 5000) {
            // reset things if necessary
            if ( (totalCount >= (Long.MAX_VALUE - count)) ||
                    (totalT >= (Long.MAX_VALUE - time)) )  {
                bytes = totalBytes = totalT = totalCount = count = 0L;
                t1 = t2;
                return;
            }

            rate = 1000.0 * ((double) count) / time;
            totalCount += count;
            totalT += time;
            avgRate = 1000.0 * ((double) totalCount) / totalT;
            // Event rates
            System.out.println("Events = " + String.format("%.3g", rate) +
                    " Hz,    avg = " + String.format("%.3g", avgRate));

            // Data rates
            rate    = ((double) bytes) / time;
            avgRate = ((double) totalBytes) / totalT;
            System.out.println("  Data = " + String.format("%.3g", rate) +
                    " kB/s,  avg = " + String.format("%.3g", avgRate) + "\n");

            bytes = count = 0L;
            t1 = System.currentTimeMillis();
        }

    }
}
