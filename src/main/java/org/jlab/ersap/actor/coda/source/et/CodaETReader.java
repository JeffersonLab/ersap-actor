package org.jlab.ersap.actor.coda.source.et;

import org.jlab.coda.et.*;
import org.jlab.coda.et.enums.Mode;
import org.jlab.coda.et.enums.Modify;
import org.jlab.coda.et.exception.EtClosedException;
import org.jlab.coda.et.exception.EtDeadException;
import org.jlab.coda.et.exception.EtException;
import org.jlab.ersap.actor.util.FadcUtil;
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
 * @author gurjyan on 2/13/23, based on Carl Timmer's code
 * @project ersap-coda
 * <p>
 * <p>
 * Establishes a connection to the ET running on a local host.
 * That is, one ET for each node that is responsible for running the ERSAP program.
 */
public class CodaETReader implements ISourceReader {

    private EtSystem sys;

    EtAttachment att;
    private String statName = "ESAPStation";
    int chunk = 10;

    // array of events
    private EtEvent[] mevs;

    private int len;
    private long t1 = 0L, t2 = 0L, time, totalT = 0L, count = 0L, totalCount = 0L, bytes = 0L, totalBytes = 0L;
    private double rate, avgRate;

    private int entryBufEvtCount=0;

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

            // Create station after all other stations
            EtStationConfig statConfig = new EtStationConfig();
            EtStation stat = sys.createStation(statConfig, statName, EtConstants.end, EtConstants.end);

            // Attach to new station
            att = sys.attach(stat);

            // keep track of time
            t1 = System.currentTimeMillis();
        } catch (Exception ex) {
            System.out.println("Error using ET system as consumer");
            ex.printStackTrace();
        }
    }

    /**
     * Gets a single event from an ET entry buffer
     *
     * @return event as a ByteBuffer
     * @throws IOException
     * @throws EtDeadException
     * @throws EtClosedException
     * @throws EtException
     */
    private ByteBuffer getEtEvent() throws IOException, EtDeadException, EtClosedException, EtException {

        // Get event's data buffer
        ByteBuffer buf = mevs[entryBufEvtCount].getDataBuffer();
        // Data length in bytes
        len = mevs[entryBufEvtCount].getLength();
        bytes += len;
        totalBytes += len;

        // Increment ET entry buffer event count
        entryBufEvtCount++;

        return buf;
    }

    @Override
    public Object nextEvent() {
        ByteBuffer buf = null;
        try {
            if ((mevs == null) || (entryBufEvtCount == mevs.length)) {

                // Put events back into ET system if we're done with all chunk of them
                if ((entryBufEvtCount > 0) && (mevs != null) && (entryBufEvtCount == mevs.length)) {
                    sys.putEvents(att, mevs);
                    count += entryBufEvtCount;
                }

                // Get chunk more events (ET buffer) from ET system
                mevs = sys.getEvents(att, Mode.SLEEP, Modify.ANYTHING, 0, chunk);
                entryBufEvtCount = 0;
            }

            // Get and return a single event from the ET entry buffer
            buf = getEtEvent();

            return FadcUtil.parseEtEvent(buf);


        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
    }

    @Override
    public int getEventCount() {
        return -1; // unbounded/streaming
    }

    @Override
    public ByteOrder getByteOrder() {
        return FadcUtil.currentDataByteOrder;
    }

    @Override
    public void close() {
        sys.close();
    }

    private void printStatistics() {
        // calculate the event rate
        t2 = System.currentTimeMillis();
        time = t2 - t1;

        if (time > 5000) {
            // reset things if necessary
            if ((totalCount >= (Long.MAX_VALUE - count)) ||
                    (totalT >= (Long.MAX_VALUE - time))) {
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
            rate = ((double) bytes) / time;
            avgRate = ((double) totalBytes) / totalT;
            System.out.println("  Data = " + String.format("%.3g", rate) +
                    " kB/s,  avg = " + String.format("%.3g", avgRate) + "\n");

            bytes = count = 0L;
            t1 = System.currentTimeMillis();
        }
    }
}
