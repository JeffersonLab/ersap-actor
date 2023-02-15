package org.jlab.ersap.actor.sampa.proc;

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

import org.jlab.ersap.actor.sampa.source.decoder.IDecoder;
import org.jlab.ersap.actor.sampa.source.ring.SRingRawEvent;
import org.jlab.ersap.actor.sampa.EMode;
import org.jlab.ersap.actor.sampa.source.decoder.DasDecoder;
import org.jlab.ersap.actor.sampa.source.decoder.DspDecoder;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;


/**
 * This class is designed to take the place of the SReceiver and receive all data coming
 * from the SAMPA board. It then just dumps it, but prints out how many frames were read.
 * Useful only for testing the data flow from the board to the first receiving component.
 * Really designed for DAS mode.
 *
 * @author timmer
 */
public class ReceiveAndDumper extends Thread {

    /** Input data stream carrying data from the client. */
    private DataInputStream dataInputStream;

    /** ID number of the data stream. */
    private final int streamId;

    private final int sampaPort;

    /** Buffer used to read a single frame of data. */
    private final ByteBuffer frameBuffer;

    /** Array wrapped by frameBuffer. */
    private final byte[] frameArray = new byte[16];

    /** Int array holding frame data in word form. */
    private final int[] data = new int[4];

    /** Type of data coming from SAMPA board. */
    private final EMode EMode;

    /** Object used to decode the data. */
    private final IDecoder iDecoder;

    /** Total number of frames consumed before printing stats and exiting. */
    private final int streamFrameLimit;


    /**
     * Constructor.
     *
     * @param sampaPort TCP server port.
     * @param streamId  data stream id number.
     * @param streamFrameLimit total number of frames consumed before printing stats and exiting.
     * @param EMode type of data coming over TCP client's socket.
     * @param byteSize  size in bytes of each raw event's internal buffer.
     */
    public ReceiveAndDumper(int sampaPort,
                            int streamId,
                            int streamFrameLimit,
                            EMode EMode,
                            int byteSize) {

        this.sampaPort = sampaPort;
        this.streamId = streamId;
        this.streamFrameLimit = streamFrameLimit;
        this.EMode = EMode;

        frameBuffer = ByteBuffer.wrap(frameArray);
        frameBuffer.order(ByteOrder.LITTLE_ENDIAN);

        boolean verbose = false;

        if (EMode.isDSP()) {
            iDecoder = new DspDecoder(verbose);
        }
        else {
            iDecoder = new DasDecoder(verbose, streamId, byteSize);
        }
    }

    public void processOneFrame(SRingRawEvent rawEvent) throws IOException {
        frameBuffer.clear();

        dataInputStream.readFully(frameArray);

        data[3] = frameBuffer.getInt();
        data[2] = frameBuffer.getInt();
        data[1] = frameBuffer.getInt();
        data[0] = frameBuffer.getInt();

        try {
            iDecoder.decodeSerial(data, rawEvent);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void readAndDumpOneFrame() throws IOException {
        dataInputStream.readFully(frameArray);
    }


    public void run1() {
        // Connecting to the sampa stream source
        try {
            ServerSocket serverSocket = new ServerSocket(sampaPort);
            System.out.println("Server is listening on port " + sampaPort);
            Socket socket = serverSocket.accept();
            System.out.println("SAMPA client connected");
            InputStream input = socket.getInputStream();
            dataInputStream = new DataInputStream(new BufferedInputStream(input, 65536));
        } catch (IOException e) {
            e.printStackTrace();
        }

        int frameCount = 0;

        try {
            //SRingRawEvent rawEvent = new SRingRawEvent(SampaType.DAS);

            do {
                // Read another frame
                //rawEvent.reset();
                //processOneFrame(rawEvent);
                readAndDumpOneFrame();

                frameCount++;

                if (frameCount % 1000 == 0) {
                    System.out.println("Receiver " + streamId + ": read " + frameCount + " frames");
                }

                // Loop until we run into our given limit of frames
            } while (frameCount < streamFrameLimit);

            System.out.println("Receiver " + streamId + ": quitting as frame limit reached, " + streamFrameLimit);

        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void run() {
        // Connecting to the sampa stream source
        try {
            ServerSocket serverSocket = new ServerSocket(sampaPort);
            System.out.println("Server is listening on port " + sampaPort);
            Socket socket = serverSocket.accept();
            System.out.println("SAMPA client connected");
            InputStream input = socket.getInputStream();
            dataInputStream = new DataInputStream(new BufferedInputStream(input, 65536));
        } catch (
                IOException e) {
            e.printStackTrace();
        }
        int frameCount = 0;

        try {

            SRingRawEvent rawEvent = new SRingRawEvent(EMode.DAS);

            do {
                rawEvent.reset();

                // Fill event with data until it's full or hits the frame limit
                do {
                    processOneFrame(rawEvent);
                    // readAndDumpOneFrame();
                    frameCount++;
                    // Loop until event is full or we run into our given limit of frames

                    //System.out.println("decoder.remaining() = " + ((DasDecoder)sampaDecoder).sampa_stream_low_.remaining() + ", full = " + sampaDecoder.isFull());

                } while ( !(iDecoder.isFull() || ((streamFrameLimit != 0) && (frameCount >= streamFrameLimit))) );

                if (EMode.isDSP()) {
                    if (rawEvent.isFull()) {
                        // Update the block number since the event becomes full once
                        // a complete block of data has been written into it.
                        rawEvent.setBlockNumber(iDecoder.incrementBlockCount());
                    }
                }
                else {
                    ((DasDecoder) iDecoder).transferData(rawEvent);
                    // ByteBuffer[] data = rawEvent.getData();
                    // if (streamId == 2) System.out.println("Transferred buf: pos = " + data[0].position() + ", lim = " + data[0].limit());
                    if (streamId == 2) System.out.println("Transferred str2 at framecount = " + frameCount);
                }

                // Loop until we run into our given limit of frames
            } while ((streamFrameLimit == 0) || (frameCount < streamFrameLimit));
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }



}
