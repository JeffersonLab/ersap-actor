package org.jlab.ersap.actor.sampa.engine;

import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.std.services.AbstractEventReaderService;
import org.jlab.epsci.ersap.std.services.EventReaderException;
import org.jlab.ersap.actor.datatypes.DasDataType;
import org.jlab.ersap.actor.sampa.EMode;
import org.jlab.ersap.actor.sampa.source.SReceiveDecodeAggregate;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Copyright (c) 2021, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * @author gurjyan on 2/15/23
 * @project ersap-coda
 */
public class SampaDASSourceEngine extends AbstractEventReaderService<SReceiveDecodeAggregate> {
    private static final String SMP_PORT = "port";
    // Total number of Front End Cards (FEC), assuming that each FEC has 2 GBT streams
    private static String FEC = "fec";
    private Process tReadoutProcess;


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
    private void startupTReadout() throws IOException {
        ProcessBuilder builder = new ProcessBuilder();
        // Initial port = 6000, mask=0x7 that means we only read FEC 0,1 and 2
        builder.command("sh", "-c", "treadout --data-type 1 --frames 4000 --mode das --mask 0x7 --port 6000 --host_ip localhost --events 0");
        tReadoutProcess = builder.start();
        StreamGobbler streamGobbler =
                new StreamGobbler(tReadoutProcess.getInputStream(), System.out::println);
        Executors.newSingleThreadExecutor().submit(streamGobbler);
        //int exitCode = treadoutProcess.waitFor();
        //assert exitCode == 0;
    }


    @Override
    protected SReceiveDecodeAggregate createReader(Path file, JSONObject opts)
            throws EventReaderException {
        int initialPort = opts.has(SMP_PORT) ? opts.getInt(SMP_PORT) : 6000;
        ArrayList<Integer> activePorts = new ArrayList<>();
        if (opts.has(FEC)) {
            String fec =opts.getString(FEC);
            String [] tokens = fec.split(",");
            for (String token : tokens) {
                switch (token.trim()) {
                    case "1":
                        activePorts.add(initialPort + 0);
                        activePorts.add(initialPort + 1);
                        break;
                    case "2":
                        activePorts.add(initialPort + 2);
                        activePorts.add(initialPort + 3);
                        break;
                    case "3":
                        activePorts.add(initialPort + 4);
                        activePorts.add(initialPort + 5);
                        break;
                    case "4":
                        activePorts.add(initialPort + 6);
                        activePorts.add(initialPort + 7);
                        break;
                    case "5":
                        activePorts.add(initialPort + 8);
                        activePorts.add(initialPort + 9);
                        break;
                }
            }
        }


        // This is the initial port, assuming that treadout will send each link/stream data to
        // sequential ports starting from initialPort (e.g. 6000, 6001, 6002, etc.)
        try {
            SReceiveDecodeAggregate v =
                    new SReceiveDecodeAggregate(EMode.DAS, activePorts);
            // start up receivers and aggregator
            v.start();

            // Sleep briefly so the receivers-aggregator can start first
//            Thread.sleep(100);

            // Start up code to sent sampa data to the receivers-aggregator
//            startupTReadout();

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
        tReadoutProcess.destroyForcibly();
        try {
            tReadoutProcess.waitFor();
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
//        return DasDataType.SAMPA_DAS;
    }}

