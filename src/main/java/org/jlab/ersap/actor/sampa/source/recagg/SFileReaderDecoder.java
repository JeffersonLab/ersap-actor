package org.jlab.ersap.actor.sampa.source.recagg;

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

import org.jlab.epsci.ersap.base.error.ErsapException;
import org.jlab.ersap.actor.datatypes.DasDataType;
import org.jlab.ersap.actor.sampa.EMode;
import org.jlab.ersap.actor.sampa.source.decoder.DasDecoder;
import org.jlab.ersap.actor.sampa.source.decoder.DspDecoder;
import org.jlab.ersap.actor.sampa.source.decoder.IDecoder;
import org.jlab.ersap.actor.sampa.source.ring.SRingRawEvent;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.jlab.ersap.actor.sampa.EMode.DAS;


/**
 * This class is designed to read a binary file create by the treadout ALICE program
 * decode it and preset it to the SampaDASFileSourceEngine actor
 */
public class SFileReaderDecoder {

    /**
     * Input data stream carrying data from the client.
     */
    private DataInputStream dataInputStream;

    /**
     * ID number of the data stream.
     */
    private final int streamId;

    /**
     * Buffer used to read a single frame of data.
     */
    private final ByteBuffer frameBuffer;

    /**
     * Array wrapped by frameBuffer.
     */
    private final byte[] frameArray = new byte[16];

    /**
     * Int array holding frame data in word form.
     */
    private final int[] data = new int[4];

    /**
     * Type of data coming from SAMPA board.
     */
    private final EMode EMode;

    /**
     * Object used to decode the data.
     */
    private final IDecoder iDecoder;

    /**
     * Total number of frames consumed before printing stats and exiting.
     */
    private final int streamFrameLimit;

    /**
     * Is the incoming data format DAS?
     */
    private final boolean isDAS;

    /**
     * Is the incoming data format DSP?
     */
    private final boolean isDSP;

    public SFileReaderDecoder(String fileName,
                              int streamId,
                              int streamFrameLimit,
                              EMode EMode,
                              int byteSize) {

        this.streamId = streamId;
        this.streamFrameLimit = streamFrameLimit;
        this.EMode = EMode;

        frameBuffer = ByteBuffer.wrap(frameArray);
        frameBuffer.order(ByteOrder.LITTLE_ENDIAN);

        boolean verbose = false;

        if (EMode.isDSP()) {
            iDecoder = new DspDecoder(verbose);
            isDSP = true;
            isDAS = false;
        } else {
            iDecoder = new DasDecoder(false, streamId, byteSize);
            isDAS = true;
            isDSP = false;
        }

        try {
            dataInputStream = new DataInputStream(new FileInputStream(fileName));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Process one frame of data and place update rawEvent
     *
     * @param rawEvent event to update (write data into it if DSP, or track frames if DAS).
     * @throws IOException if error reading data.
     */
    public void processOneFrame(SRingRawEvent rawEvent) throws IOException {
        frameBuffer.clear();

        // clear gbt_frame: 4, 4-byte words
        dataInputStream.readFully(frameArray);
        data[3] = frameBuffer.getInt();
        data[2] = frameBuffer.getInt();
        data[1] = frameBuffer.getInt();
        data[0] = frameBuffer.getInt();

        try {
            iDecoder.decodeSerial(data, rawEvent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ByteBuffer getProcess() {
        int frameCount = 0;
        ByteBuffer bb = null;
        try {
            SRingRawEvent rawEvent = new SRingRawEvent(DAS);
            do {
                rawEvent.reset();
                System.out.println("DDD: P1");

                // Fill event with data until it's full or hits the frame limit
                do {
                    if(iDecoder.isFull())
                    System.out.println("DDD: "+ isDAS+" "+iDecoder.isFull());

                    processOneFrame(rawEvent);
                    frameCount++;

                    // In DSP mode, the rawEvent becomes full once "block" number of frames have been stored in it.
                    // In DAS mode, the decoder becomes full when it cannot hold any more raw data bytes and needs
                    // to pass them to the rawEvent.
                    // We also need to account for any frame limit specified on the command line.

                } while (!((isDAS && iDecoder.isFull()) ||
                        (isDSP && rawEvent.isFull()) ||
                        ((streamFrameLimit != 0) && (frameCount >= streamFrameLimit))));

                if (isDSP) {
                    if (rawEvent.isFull()) {
                        // Update the block number since the event becomes full once
                        // a complete block of data has been written into it.
                        rawEvent.setBlockNumber(iDecoder.incrementBlockCount());
                        //int blockCount = sampaDecoder.getBlockCount();
                        //if (streamId == 2 && (blockCount % 1000 == 0)) System.out.println("Raw event full, set block num to " + blockCount);
                    }
                } else {
                    ((DasDecoder) iDecoder).transferData(rawEvent);
                    //if (streamId == 1) System.out.println("Transferred str2 at framecount = " + frameCount);
                }

                // Print out
//                rawEvent.printData(System.out, streamId, false);
//                rawEvent.calculateStats();
//                rawEvent.printStats(System.out, false);

                // Loop until we run into our given limit of frames
            } while ((streamFrameLimit == 0) || (frameCount < streamFrameLimit));
            bb = DasDataType.serialize(rawEvent.getData());

        } catch (Exception e) {
            e.printStackTrace();
        }
        return bb;
    }

    public void exit() {
        try {
            dataInputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        exit();
    }
}