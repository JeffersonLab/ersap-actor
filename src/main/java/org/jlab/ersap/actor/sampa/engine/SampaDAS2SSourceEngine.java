package org.jlab.ersap.actor.sampa.engine;

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
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.std.services.AbstractEventReaderService;
import org.jlab.epsci.ersap.std.services.EventReaderException;
import org.jlab.ersap.actor.sampa.source.s2.S2RecDecAgg;
import org.jlab.ersap.actor.sampa.EMode;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.function.Consumer;


/**
 * This class creates a data source from a single Front End Card (FEC) which contains 5 Sampa chips.
 * This FEC is connected to a TRORC PCI card in a computer - currently alkaid.jlab.org.
 * The FEC DMAs data to the PCI card over 2 GBT links in the DAS data format.
 * There is a program (treadout) which then takes the data on the TRORC card and serves in over 2
 * TCP connections - one connection to each of 2 receivers. Each receiver gets 1/2 of the DAS data,
 * comprised of 80 channels, each channel stores data in its own ByteBuffer. Thus, altogether,
 * there are 160 channels.
 *
 * @author timmer
 */
public class SampaDAS2SSourceEngine extends AbstractEventReaderService<S2RecDecAgg> {

    private static final String SMP_PORT1 = "port1";
    private static final String SMP_PORT2 = "port2";

    private Process treadoutProcess;


    /**
     * This class is used to properly handle the output of commands executed in Java.
     * We need a way to hook into the input and output streams of a started external process.
     * At least the output must be consumed – otherwise our process doesn't return successfully,
     * instead it will hang. Let's implement a commonly used class called StreamGobbler
     * which consumes an InputStream:
     */
    private static class StreamGobbler implements Runnable {
        private InputStream inputStream;
        private Consumer<String> consumer;

        public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
            this.inputStream = inputStream;
            this.consumer = consumer;
        }

        @Override
        public void run() {
            new BufferedReader(new InputStreamReader(inputStream)).lines().forEach(consumer);
        }
    }


    /**
     * This method starts up the "treadout" process which gets the 2 streams of data
     * written by a single Sampa FEC into a single TRORC PCI card and sends it over the network
     * to 2 receivers or in this case the combo of 2 receivers and 1 aggregator.
     * @throws IOException for any IO error.
     */
    private void startupTreadout() throws IOException {
        ProcessBuilder builder = new ProcessBuilder();
        builder.command("sh", "-c", "treadout --data-type 1 --frames 2000 --mode das --events 0");
        treadoutProcess = builder.start();
        StreamGobbler streamGobbler =
                new StreamGobbler(treadoutProcess.getInputStream(), System.out::println);
        Executors.newSingleThreadExecutor().submit(streamGobbler);
        //int exitCode = treadoutProcess.waitFor();
        //assert exitCode == 0;
    }


    @Override
    protected S2RecDecAgg createReader(Path file, JSONObject opts)
            throws EventReaderException {
        int port1 = opts.has(SMP_PORT1) ? opts.getInt(SMP_PORT1) : 6000;
        int port2 = opts.has(SMP_PORT2) ? opts.getInt(SMP_PORT2) : 6001;
        try {
            S2RecDecAgg v =
                    new S2RecDecAgg(port1, port2,
                            1, 2,
                            0, EMode.DAS);
            // start up receivers and aggregator
            v.start();

            // Sleep briefly so the receivers-aggregator can start first
//            Thread.sleep(1);

            // Start up code to sent sampa data to the receivers-aggregator
//            startupTreadout();

            return v;
        }
        catch (Exception e) {
            throw new EventReaderException(e);
        }
    }

    @Override
    protected void closeReader() {
        // Best thing is to stop treadout first or else it may hang
        // subsequent attempts to use the Sampa FECs currently in use.
        treadoutProcess.destroyForcibly();
        try {
            treadoutProcess.waitFor();
        } catch (InterruptedException e) {}

        // Now close the receiver-aggregator
        reader.close();
    }

    @Override
    protected int readEventCount() throws EventReaderException {
        return Integer.MAX_VALUE;
    }

    @Override
    protected ByteOrder readByteOrder() throws EventReaderException {
        return ByteOrder.LITTLE_ENDIAN;
    }

    @Override
    protected Object readEvent(int eventNumber) throws EventReaderException {
//        System.out.println("DDD: Read event...");
        return reader.getEvent();
    }

    @Override
    protected EngineDataType getDataType() {
        return EngineDataType.BYTES;
//        return SampaDasType.SAMPA_DAS;
    }
}
