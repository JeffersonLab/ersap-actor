package org.jlab.ersap.actor.coda.oldvtp;

import com.lmax.disruptor.RingBuffer;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Copyright (c) 2021, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * @author gurjyan on 2/23/23
 * @project ersap-coda
 */
public class VReceiver extends Thread {

    /**
     * VTP data stream
     */
    private DataInputStream dataInputStream;

    /**
     * Stream ID
     */
    private int streamId;

    /**
     * Output ring
     */
    private RingBuffer<VRingRawEvent> ringBuffer;

    /**
     * Current spot in the ring from which an item was claimed.
     */
    private long sequenceNumber; // This does NOT have to be atomic, Carl T

    // server socket
    private ServerSocket serverSocket;

    // control for the thread termination
    private AtomicBoolean running = new AtomicBoolean(true);


    /**
     * For statistics
     */
    private int vtpPort;
    private int statPeriod;
    private double totalData;
    private int rate;
    private long missed_record;
    private long prev_rec_number;
    private ByteBuffer headerBuffer;
    private byte[] header = new byte[52];

    public VReceiver(int vtpPort, int streamId, RingBuffer<VRingRawEvent> ringBuffer, int statPeriod) {
        this.vtpPort = vtpPort;
        this.ringBuffer = ringBuffer;
        this.streamId = streamId;
        this.statPeriod = statPeriod;

        headerBuffer = ByteBuffer.wrap(header);

        headerBuffer.order(ByteOrder.LITTLE_ENDIAN);

        // Timer for measuring and printing statistics.
        Timer timer = new Timer();
        timer.schedule(new PrintRates(false), 0, statPeriod * 1000);
    }

    /**
     * Get the next available item in ring buffer for writing data.
     *
     * @return next available item in ring buffer.
     * @throws InterruptedException if thread interrupted.
     */
    private VRingRawEvent get() throws InterruptedException {

        sequenceNumber = ringBuffer.next();
        VRingRawEvent buf = ringBuffer.get(sequenceNumber);
        return buf;
    }

    private void publish() {
        ringBuffer.publish(sequenceNumber);
    }


    private void decodeVtpHeader(VRingRawEvent evt) {
        try {
            headerBuffer.clear();
            dataInputStream.readFully(header);

            int source_id = headerBuffer.getInt();
            int total_length = headerBuffer.getInt();
            int payload_length = headerBuffer.getInt();
            int compressed_length = headerBuffer.getInt();
            int magic = headerBuffer.getInt();

            int format_version = headerBuffer.getInt();
            int flags = headerBuffer.getInt();

            long record_number = EUtil.llSwap(headerBuffer.getLong());
            long ts_sec = EUtil.llSwap(headerBuffer.getLong());
            long ts_nsec = EUtil.llSwap(headerBuffer.getLong());

//            long record_number = headerBuffer.getLong();
//            long ts_sec = headerBuffer.getLong();
//            long ts_nsec = headerBuffer.getLong();

//            printFrame(streamId, source_id, total_length, payload_length,
//                    compressed_length, magic, format_version, flags,
//                    record_number, ts_sec, ts_nsec);

            if (evt.getPayload().length < payload_length) {
                byte[] payloadData = new byte[payload_length];
                evt.setPayload(payloadData);
            }
            dataInputStream.readFully(evt.getPayload(), 0, payload_length);

//            evt.setRecordNumber(rcn);
            evt.setPayloadDataLength(payload_length);
            evt.setRecordNumber(record_number);
            evt.setStreamId(streamId);

            // Collect statistics
            long tmp = missed_record + (record_number - (prev_rec_number + 1));
            missed_record = tmp;

            prev_rec_number = record_number;
            totalData = totalData + (double) total_length / 1000.0;
            rate++;

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run() {
        // Connecting to the VTP stream source
        try {
            serverSocket = new ServerSocket(vtpPort);
            System.out.println("Server is listening on port " + vtpPort);
            Socket socket = serverSocket.accept();
            System.out.println("VTP client connected");
            InputStream input = socket.getInputStream();
//            dataInputStream = new DataInputStream(new BufferedInputStream(input));
            dataInputStream = new DataInputStream(new BufferedInputStream(input, 65536)); //CT suggestion
            dataInputStream.readInt();
            dataInputStream.readInt();
        } catch (
                IOException e) {
            e.printStackTrace();
        }

        while (running.get()) {
            try {
                // Get an empty item from ring
                VRingRawEvent buf = get();

                decodeVtpHeader(buf);

                // Make the buffer available for consumers
                publish();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
    }

    public void exit() {
        running.set(false);
        try {
            dataInputStream.close();
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.interrupt();
    }

    private class PrintRates extends TimerTask {
        BufferedWriter bw;
        boolean f_out;

        public PrintRates(boolean file_out) {
            f_out = file_out;
            if (f_out) {
                try {
                    bw = new BufferedWriter(new FileWriter("stream_" + streamId + ".csv"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void run() {
            long m_rate = missed_record / statPeriod;
            if (f_out) {
                try {
                    bw.write(m_rate + "\n");
                    bw.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            System.out.println(" stream:" + streamId
                    + " frame rate =" + rate / statPeriod
                    + " Hz. event rate =" + VTP1StreamReceiverDecoder.ebEvents / statPeriod
                    + " Hz. data rate =" + totalData / statPeriod + " kB/s."
                    + " missed rate = " + m_rate + " Hz."
                    + " record number = " + prev_rec_number
            );
            rate = 0;
            totalData = 0;
            missed_record = 0;
            VTP1StreamReceiverDecoder.ebEvents = 0;
        }
    }

}
