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


import org.jlab.ersap.actor.sampa.EMode;

import static org.jlab.ersap.actor.sampa.EMode.DAS;
import static org.jlab.ersap.actor.sampa.EMode.DSP;

/**
 * Class to work with ReceiveAndDumper. It starts 2 of those objects which acts as
 * replacements for SReceivers. They read, then dump data in a cycle for a max of the
 * given number of frames.
 * Useful only for testing the data flow from the board to the first receiving component.
 * @author timmer
 */
public class DoubleDumper {

    /**
     * SAMPA ports and stream info
     */
    private final int sampaPort1;
    private final int sampaPort2;
    private final int streamId1;
    private final int streamId2;
    private final int streamFrameLimit;
    private final int byteSize;

    /** Format of data SAMPA chips are sending. */
    private EMode EMode;


    /**
     * Constructor.
     *
     * @param sampaPort1       TCP port for first  data producer to connect to.
     * @param sampaPort2       TCP port for second data producer to connect to.
     * @param streamId1        id number of first  data stream / producer.
     * @param streamId2        id number of second data stream / producer.
     * @param streamFrameLimit total number of frames consumed before exiting.
     * @param EMode        type of data coming over TCP sockets.
     */
    public DoubleDumper(int sampaPort1, int sampaPort2,
                        int streamId1, int streamId2,
                        int streamFrameLimit,
                        EMode EMode,
                        int byteSize) {

        this.sampaPort1 = sampaPort1;
        this.sampaPort2 = sampaPort2;
        this.streamId1 = streamId1;
        this.streamId2 = streamId2;
        this.streamFrameLimit = streamFrameLimit;
        this.EMode = EMode;
        this.byteSize = byteSize;
    }

    public void go() {
        ReceiveAndDumper receiver1 = new ReceiveAndDumper(sampaPort1, streamId1, streamFrameLimit, EMode, byteSize);
        ReceiveAndDumper receiver2 = new ReceiveAndDumper(sampaPort2, streamId2, streamFrameLimit, EMode, byteSize);

        receiver1.start();
        receiver2.start();
    }

    /**
     * Main method. Arguments are:
     * <ol>
     * <li>port of TCP server to run in first ReceiveAndDumper object
     * <li>port of TCP server to run in second ReceiveAndDumper object
     * <li>id of first ReceiveAndDumper's data stream
     * <li>id of second ReceiveAndDumper's data stream
     * <li>limit on number of frames to parse on each stream
     * <li>optional: if = DAS, it switches from parsing DSP format to DAS format data
     * </ol>
     *
     * Really only designed for DAS mode.
     *
     * @param args array of args.
     */
    public static void main(String[] args) {
        int port1 = Integer.parseInt(args[0]);
        int port2 = Integer.parseInt(args[1]);

        int streamId1 = Integer.parseInt(args[2]);
        int streamId2 = Integer.parseInt(args[3]);

        int streamFrameLimit = Integer.parseInt(args[4]);

        int byteSize = 8192;

        EMode sampaType = DSP;

        if (args.length > 5) {
            String sType = args[5];
            if (sType.equalsIgnoreCase("das")) {
                sampaType = DAS;
            }
        }

        try {
            new DoubleDumper(port1, port2, streamId1, streamId2,
                    streamFrameLimit, sampaType, byteSize).go();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
