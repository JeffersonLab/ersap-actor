package org.jlab.ersap.actor.sampa.source.decoder;

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

import org.jlab.ersap.actor.sampa.source.ring.SRingRawEvent;
import org.jlab.ersap.actor.sampa.EMode;
import org.jlab.ersap.actor.sampa.proc.ELinkStats;

import java.io.OutputStream;
import java.nio.ByteBuffer;


public class DspDecoder implements IDecoder {

    /** If true, print out debug info. */
    private final boolean verbose;

    /** Link (0-27) to debug when in verbose mode. */
    private final int eLinkToDebug = 2;

    /** Object in which to keep statistics about each link. */
    private final ELinkStats eLinkStats = new ELinkStats();

    // static variables for 28 elink serial streams
    private final long[] shiftReg      = new long[28];
    private final int[] syncFound      = new int[28];
    private final int[] dataHeader     = new int[28];
    private final int[] headerBitCount = new int[28];
    private final int[] dataBitCount   = new int[28];
    private final int[] dataWordCount  = new int[28];
    private final int[] dataCount      = new int[28];
    private final int[] numWords       = new int[28];

    // There are 28 eLinks. Each link has either 2 or 3 channels' data on it.
    // There are 80 channels coming over the 28 links representing 2.5 of the 5 SAMPA chips.
    // There are 2 groups of 80 for a total of 160 channels representing all 5 chips.
    // Each of the 2, 80 channel groups go to one of 2 high speed serial links.
    //
    // The data from each link is reformatted into 2, 32-bit header words, followed by data words.
    // Each data word starts with the 10 bit ADC value, # of ADC samples, or Time. The other bits
    // contain a packet word number and 3 bits identifying it as a data word (0).

    /** Buffers, one for each link, to store data temporarily while transforming data into another format. */
    private final ByteBuffer[] eLinkDataTemp = new ByteBuffer[28];

    /** Sync pattern. */
    private static final long syncHeaderPattern = 0x1555540F00113L;

    // According to Ed,
    // Currently the SAMPA chips are programmed to transmit non-empty packet data every 1000 ADC samples.
    // The period is called the sample window and equates to 50 us when sampling at 20 MHz.
    // This corresponds to 2000 frames of the 4.8 Gb/s data stream. We define a BLOCK of packets
    // to be the set of all packets collected during N_BLOCK consecutive sampling windows.
    // N_BLOCK is programmable (see below for current val). A BLOCK HEADER is written to a file followed
    // by all the packet data of the BLOCK. For N_BOCK = 10, the BLOCK represents 500 us of time,
    // so the file written is time-ordered to this level.
    private static final int N_BLOCK = 1;
    private static final int framesInBlock = 2000 * N_BLOCK;

    /** Total number of frames parsed. */
    private long frameCount;

    /** Number of full blocks of data parsed. */
    private int blockCount;



    /** Constructor with no debug output. */
    public DspDecoder() {this(false);}


    /**
     * Constructor.
     * @param verbose if true, enable debug output.
     */
    public DspDecoder(boolean verbose) {
        this.verbose = verbose;
        eLinkStats.init(); // not necessary, but anyways

        for (int i = 0; i < 28; i++) {
            // 2 headers + (1024 samples + times + counts) * 4 bytes each -> max < 16k
            // This allocates about .5MB, but eliminates having to always check if we
            // need to expand these buffers (and then expand them).
            eLinkDataTemp[i] = ByteBuffer.allocate(16384);
        }
    }


    /**
     * Get the number of sequential sampling windows (2000 frames each) of SAMPA board per block.
     * @return number of sequential sampling windows of SAMPA board per block.
     */
    public static int getNBlock() {return N_BLOCK;}

    /**
     * Get the number of frames in 1 block.
     * @return number of frames in 1 block.
     */
    public static int getFramesInBlock() {return framesInBlock;}

    /** {@inheritDoc} */
    public EMode getSampaType() {return EMode.DSP;}

    /** {@inheritDoc} */
    public int getBlockCount() {return blockCount;}

    /** {@inheritDoc} */
    public int incrementBlockCount() { return ++blockCount;}

    /** {@inheritDoc} */
    public void printStats(OutputStream out, boolean json) {eLinkStats.write(out);}

    /** {@inheritDoc} */
    public long getFrameCount() {return frameCount;}

    /** Not applicable to DSP mode. Do nothing. */
    public void reSync() {}

    /** Not applicable to DSP mode. Do nothing. */
    public boolean isFull() {return false;}


    /** {@inheritDoc} */
    public void decodeSerial(int[] gbt_frame, SRingRawEvent rawEvent) {

        int bitValue;
        int dataWord;
        int pkt;
        int hadd;
        int chadd;
        int bxCount = 0;
        int hamming;
        int parity;
        int dataParity;
        int head1;
        int head2;
        int dataValue;
        int gFrameWord;
        int ii_min;
        int ii_max;
        int fec_channel;
        boolean match;

        rawEvent.incrementFramesStored();
        frameCount++;

        // Loop thru all 28 eLinks ...
        for (int eLink = 0; eLink < 28; eLink++) {

            if (eLink < 8) {
                gFrameWord = gbt_frame[0];
            } else if (eLink < 16) {
                gFrameWord = gbt_frame[1];
            } else if (eLink < 24) {
                gFrameWord = gbt_frame[2];
            } else {
                gFrameWord = gbt_frame[3];
            }
            ii_min = (eLink % 8) * 4;
            ii_max = ii_min + 3;

            // find sync header - this will run until first sync packet header is found
            // sync packet header pattern
            if (syncFound[eLink] == 0) {
                for (int ii = ii_max; ii >= ii_min; ii--) {
                    // elink (4 bits per frame)
                    bitValue = (gFrameWord >>> ii) & 1;
                    // bitValue = (gFrameWord & (0x00000001 << ii)) >> ii;

                    if (bitValue == 1) {
                        if (verbose && (eLink == eLinkToDebug)) {
                            System.out.println("-> " + ii + " " + Long.toHexString(shiftReg[eLink]));
                        }

                        shiftReg[eLink] = shiftReg[eLink] | 0x0004000000000000L; // set bit 50 in shiftReg

                        if (verbose && (eLink == eLinkToDebug)) {
                            System.out.println("-> " + ii + " " + Long.toHexString(shiftReg[eLink]));
                        }
                    }
                    // Carl, fix bug, needed a >>> instead of >>
                    shiftReg[eLink] = shiftReg[eLink] >>> 1;

                    if (verbose && (eLink == eLinkToDebug)) {
                        System.out.println("DDD-> " + ii + " " + Integer.toHexString(gFrameWord) + " " + Integer.toHexString(bitValue));
                        System.out.println("elink = " + eLink + " shiftReg = " + Long.toHexString(shiftReg[eLink]));
                    }
                    if (syncFound[eLink] != 0) {
                        // when sync found count remaining bits of frame for next header
                        headerBitCount[eLink]++;
                    }
                    if (shiftReg[eLink] == syncHeaderPattern) {
                        // check if sync packet header detected
                        syncFound[eLink] = 1;
                        eLinkStats.getSyncFoundCount()[eLink]++;
                        eLinkStats.getSyncCount()[eLink]++;
                        headerBitCount[eLink] = 0;
                        if (verbose && (eLink == eLinkToDebug)) {
                            System.out.println("DDD:  ****************|| SYNC HEADER  elink = " + eLink + " ||****************** ");
                        }
                    }
                }
                if (syncFound[eLink] != 0) {
                    // print headerBitCount after frame where sync packet found
                    if (verbose && (eLink == eLinkToDebug)) {
                        System.out.println("DDD: SyncPacket found headerBitCount = " + headerBitCount[eLink]);
                    }
                }
            }
            else if (dataHeader[eLink] == 0) {
                // runs only after first sync packet header has been found
                // we find NEXT header here
                for (int ii = ii_max; ii >= ii_min; ii--) {
                    // elink 0 (4 bits per frame)
                    bitValue = (gFrameWord >>> ii) & 1;
                    if (bitValue == 1) {
                        shiftReg[eLink] = shiftReg[eLink] | 0x0004000000000000L;        // set bit 50 in shiftReg
                    }
                    shiftReg[eLink] = shiftReg[eLink] >>> 1;

                    if (dataHeader[eLink] > 0) {
                        // AFTER data header is found count remaining bits of frame as data bits
                        dataBitCount[eLink]++;
                    } else {
                        // count frame bits as header bits not data type
                        headerBitCount[eLink]++;
                    }
//          -----------------------------------------------------------------------
                    if (headerBitCount[eLink] == 50) {
                        // next packet header - decode
                        if (shiftReg[eLink] == syncHeaderPattern) {
                            // sync header
                            eLinkStats.getSyncCount()[eLink]++;
                            headerBitCount[eLink] = 0;

                            if (verbose && (eLink == eLinkToDebug)) {
                                System.out.println("DDD: **************** SYNC HEADER  elink = " + eLink +
                                        " shiftReg = 0x" + Long.toHexString(shiftReg[eLink]) +
                                        " syncCount = " + eLinkStats.getSyncCount()[eLink]);
                            }
                        } else {
                            // non-sync packet header - identify type
                            pkt             = (int) ((shiftReg[eLink] >>> 7) & 0x7);
                            numWords[eLink] = (int) ((shiftReg[eLink] >>> 10) & 0x3FF);
                            hadd            = (int) ((shiftReg[eLink] >>> 20) & 0xF);
                            chadd           = (int) ((shiftReg[eLink] >>> 24) & 0x1F);
                            bxCount         = (int) ((shiftReg[eLink] >>> 29) & 0xFFFFF);
                            hamming         = (int) ((shiftReg[eLink]) & 0x3F);
                            dataParity      = (int) ((shiftReg[eLink] >>> 49) & 0x1);
                            parity          = (int) ((shiftReg[eLink] >>> 6) & 0x1);

                            // Carl, this is different than Ed's, Ed's code looks like an error
                            if ((pkt == 0) && (numWords[eLink] == 0) && (chadd == 0x15)) {
                                // heartbeat packet (NO payload) - push into output stream
                                eLinkStats.getHeartBeatCount()[eLink]++;
                                headerBitCount[eLink] = 0;
                                head1 = 0xA0000000 | (bxCount << 9) | (chadd << 4) | hadd;
                                head2 = 0x40000000 | (parity << 23) | (hamming << 17) | (dataParity << 16) | (numWords[eLink] << 3) | pkt;

                                if (verbose && (eLink == eLinkToDebug)) {
                                    System.out.println("DDD: **************** HEARTBEAT HEADER  elink = " + eLink
                                            + " shiftReg = 0x" + Long.toHexString(shiftReg[eLink]) +
                                            " heartBeatCount = " + eLinkStats.getHeartBeatCount()[eLink]);
                                }
                            }
                            else if (pkt == 4) {
                                // initially require only NORMAL data packet headers
                                // check consistency of data header - verify that 'hadd' and chadd' are consistent with 'eLink' number
                                match = matchDataHeader(eLink, hadd, chadd);

                                if (match) {
                                    // header consistent with data header
                                    dataCount[eLink]++;
                                    eLinkStats.getDataHeaderCount()[eLink]++;
                                    fec_channel = (hadd * 32) + chadd;
                                    if ((fec_channel >= 0) && (fec_channel <= 159)) {
                                        eLinkStats.getDataChannelCount()[fec_channel]++;
                                    }
                                    else if (verbose) {
                                        System.out.println("DDD:  -------- ILLEGAL CHANNEL NUMBER  elink = " + eLink +
                                                " hadd = " + hadd + " chadd = " + chadd);
                                    }
                                    dataHeader[eLink] = 1;
                                    dataBitCount[eLink] = 0;
                                    dataWordCount[eLink] = 0;
                                    headerBitCount[eLink] = 0;
                                    head1 = 0xA0000000 | (bxCount << 9) | (chadd << 4) | hadd;
                                    head2 = 0x40000000 | (parity << 23) | (hamming << 17) | (dataParity << 16) | (numWords[eLink] << 3) | pkt;

                                    // push header into temporary storage vector
                                    eLinkDataTemp[eLink].putInt(head1);
                                    eLinkDataTemp[eLink].putInt(head2);

                                    if (verbose && (eLink == eLinkToDebug)) {
                                        System.out.println("DDD: **************** DATA HEADER  elink = " + eLink +
                                                " shiftReg = 0x" + Long.toHexString(shiftReg[eLink]) +
                                                " pkt = " + pkt +
                                                " dataCount = " + dataCount[eLink] +
                                                " numWords = " + numWords[eLink] +
                                                " hadd = " + hadd +
                                                " chadd = " + chadd +
                                                " bxCount = " + bxCount);
                                    }
                                } else {
                                    // inconsistent data header - force the finding of next sync header
                                    headerBitCount[eLink] = 0;
                                    syncFound[eLink] = 0;
                                    eLinkStats.getSyncLostCount()[eLink]++;
                                    if (verbose && (eLink == eLinkToDebug)) {
                                        System.out.println("DDD: UNRECOGNIZED HEADER  elink = " + eLink +
                                                " shiftReg = 0x" + Long.toHexString(shiftReg[eLink]) +
                                                " pkt = ");
                                    }
                                }
                            } else {
                                // 'unrecognized' header - force the finding of next sync header
                                headerBitCount[eLink] = 0;
                                syncFound[eLink] = 0;
                                eLinkStats.getSyncLostCount()[eLink]++;
                                if (verbose && (eLink == eLinkToDebug)) {
                                    System.out.println("DDD: -------- UNRECOGNIZED HEADER  elink = " + eLink +
                                            " shiftReg = 0x" + Long.toHexString(shiftReg[eLink]) +
                                            " pkt = " + pkt);
                                }
                            }
                        }
                    }
                    //-----------------------------------------------------------------------
                }
            }
            else if (dataHeader[eLink] > 0) {
                // runs only after data packet header has been found
                for (int ii = ii_max; ii >= ii_min; ii--) {
                    // elink (4 bits per frame)
                    // Carl, more efficient to do:
                    bitValue = (gFrameWord >>> ii) & 1;
                    // bitValue = (gFrameWord & (0x00000001 << ii)) >>> ii;
                    if (bitValue > 0)
                        shiftReg[eLink] = shiftReg[eLink] | 0x0004000000000000L;        // set bit 50 in shiftReg
                    shiftReg[eLink] = shiftReg[eLink] >>> 1;

                    if (dataHeader[eLink] > 0)       // count data word bits until data payload is exhausted
                        dataBitCount[eLink]++;
                    else                            // if payload is exhausted count remaining bits of frame for next header
                        headerBitCount[eLink]++;

                    if (dataBitCount[eLink] == 10) {
                        // print data word
                        dataWordCount[eLink]++;
                        dataWord = (int) ((shiftReg[eLink] >>> 40) & 0x3FF);
                        dataValue = (dataWordCount[eLink] << 16) | dataWord;
                        eLinkDataTemp[eLink].putInt(dataValue);
                        dataBitCount[eLink] = 0;

                        if (verbose && (eLink == eLinkToDebug)) {
                            System.out.println("DDD:  shiftReg = " + shiftReg[eLink] +
                                    " data = 0x" + Integer.toHexString(dataWord) +
                                    " data = " + dataWord +
                                    " dataWordCount = " + dataWordCount[eLink] +
                                    " elink = " + eLink);
                        }
                    }

                    if (dataWordCount[eLink] == numWords[eLink]) {
                        // Done with packet payload.
                        // Both header words and all packet data words have been stored in a temporary vector.
                        // This is done to assure that only complete packets appear in the output data stream.
                        // Now copy the temporary vector to the output stream.
                        int dataBytes = 4 * (numWords[eLink] + 2);
                        // Write directly into raw event's ByteBuffer.
                        // Expand it if necessary to handle all data.
                        rawEvent.setTime(bxCount);
                        ByteBuffer bb = rawEvent.expandBuffer(eLink, dataBytes);
                        System.arraycopy(eLinkDataTemp[eLink].array(), 0,
                                bb.array(), bb.position(), dataBytes);

                        // Be sure to track how much we've written to date
                        bb.position(bb.position() + dataBytes);

                        // Delete all data of temporary vector
                        eLinkDataTemp[eLink].clear();

                        // Reset
                        dataHeader[eLink] = 0;
                        headerBitCount[eLink] = 0;
                        dataBitCount[eLink] = 0;
                        dataWordCount[eLink] = 0;

                        if (verbose && (eLink == eLinkToDebug)) {
                            System.out.println("DDD: END OF DATA  elink = " + eLink);
                        }
                    }
                }
            }
        }
    }

    private boolean matchDataHeader(int eLink, int hadd, int chadd) {
        int chip_a;
        int chip_b;
        int chan_a;
        int chan_b;
        int chan_c;
        int chan_d;
        int chan_e;
        int chan_f;
        boolean chip_match;
        boolean channel_match;
        boolean match;

// check consistency of header - verify that 'hadd' and chadd' are consistent with 'eLink' number
        if (eLink < 11) {
            // eLink 0-10 are from chip 0 (link00) or chip 3 (link01)
            chip_a = 0;
            chip_b = 3;
        } else if (eLink < 22) {
            // eLink 11-21 are from chip 1 (link00) or chip 4 (link01)
            chip_a = 1;
            chip_b = 4;
        } else {
            // eLink 22-27 are from chip 2 (link00, link01)
            chip_a = 2;
            chip_b = 2;
        }

        if (eLink < 10) {
            // compare to 6 possible channel values due to complex chip 2 mapping
            chan_a = (eLink % 11) * 3;    // eLink 0 (ch 0,1,2), eLink 1 (ch 3,4,5), ... elink 9 (ch 27,28,29)
            chan_b = chan_a + 1;
            chan_c = chan_a + 2;
            chan_d = chan_a;
            chan_e = chan_b;
            chan_f = chan_c;
        } else if (eLink == 10) {
            // eLink 10 (ch 30,31)
            chan_a = 30;
            chan_b = 31;
            chan_c = 30;
            chan_d = 31;
            chan_e = 30;
            chan_f = 31;
        }
        // Carl, Ed's logic must be wrong! This if clause overwrites the  previous if statement.
        // Change   if   to   else if.
        // if (eLink < 21) {
        else if (eLink < 21) {
            // eLink 11 (ch 0,1,2), eLink 12 (ch 3,4,5), ... elink 20 (ch 27,28,29)
            chan_a = (eLink % 11) * 3;
            chan_b = chan_a + 1;
            chan_c = chan_a + 2;
            chan_d = chan_a;
            chan_e = chan_b;
            chan_f = chan_c;
        } else if (eLink == 21) {
            // eLink 21 (ch 30,31)
            chan_a = 30;
            chan_b = 31;
            chan_c = 30;
            chan_d = 31;
            chan_e = 30;
            chan_f = 31;
        } else if (eLink < 27) {
            // Link00:  eLink 22 (ch 0,1,2),    ... elink 26 (ch 12,13,14)
            // Link01:  eLink 22 (ch 15,16,17), ... elink 26 (ch 27,28,29)
            chan_a = (eLink % 22) * 3;
            chan_b = chan_a + 1;
            chan_c = chan_a + 2;
            chan_d = chan_a + 15;
            chan_e = chan_b + 15;
            chan_f = chan_c + 15;
        } else {
            // eLink 27 (ch 30,31)
            chan_a = 30;
            chan_b = 31;
            chan_c = 30;
            chan_d = 31;
            chan_e = 30;
            chan_f = 31;
        }

        chip_match = (hadd == chip_a) || (hadd == chip_b);
        channel_match = (chadd == chan_a) || (chadd == chan_b) || (chadd == chan_c)
                || (chadd == chan_d) || (chadd == chan_e) || (chadd == chan_f);

        match = chip_match && channel_match;
        return match;
    }


}

