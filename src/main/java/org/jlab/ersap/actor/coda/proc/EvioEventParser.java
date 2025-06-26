package org.jlab.ersap.actor.coda.proc;

import org.jetbrains.annotations.NotNull;
import org.jlab.coda.jevio.EvioBank;
import org.jlab.coda.jevio.EvioEvent;
import org.jlab.coda.jevio.EvioReader;
import org.jlab.coda.jevio.EvioSegment;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;


public class EvioEventParser {
    private boolean debug;

    public static ByteOrder evioDataByteOrder = ByteOrder.BIG_ENDIAN;

    public EvioEventParser(boolean debug) {
        this.debug = debug;
    }

    @NotNull
    /**
     * Parses ET event created and sent by the CODA aggregator.
     * This is going to be an evio-6 format
     */
    public EtEvent parseEtEvent(ByteBuffer buf) throws Exception {
        EvioReader r = new EvioReader(buf);
        EtEvent evt = new EtEvent();
        if (debug) System.out.println("DDD== EvioReader > version    = " + r.getEvioVersion()
                + " eventCount = " + r.getEventCount()
                + " blockCount = " + r.getBlockCount());

        for (int i = 0; i < r.getEventCount(); i++) {
            EvioEvent event = r.parseNextEvent();
            evioDataByteOrder = r.getByteOrder();
            List<RocTimeFrameBank> rocBanks = parseTimeFrame(event);
            evt.addTimeFrame(rocBanks);
        }
        return evt;
    }

    @NotNull
    public EtEvent parseFileEvent(EvioEvent event) throws Exception {

        EtEvent evt = new EtEvent();
        evioDataByteOrder = event.getByteOrder();

        List<RocTimeFrameBank> rocBanks = parseTimeFrame(event);
        evt.addTimeFrame(rocBanks);
        return evt;
    }

    private List<RocTimeFrameBank> parseTimeFrame(EvioEvent ev) throws Exception {

        List<RocTimeFrameBank> banks = new ArrayList<>();
        // Read Aggregated time frame (evio v6.0) bank header and extract event tag
        int evTag = ev.getHeader().getTag();

        // Note the event tag = 0xff60 is a built stream event
        if (debug) System.out.println("DDD=====> event tag = " + Integer.toHexString(evTag));

        if (evTag == 0xffd1) {
            System.out.println("Skip over PRESTART event");
            return null;
        } else if (evTag == 0xffd2) {
            System.out.println("Skip over GO event");
            return null;
        } else if (evTag == 0xffd4) {
            System.out.println("Hit END event, quitting");
            return null;
        }

        // Get number of children. Child is a bank or a segment
        int childCount = ev.getChildCount();

        if (childCount < 2) {
            throw new Exception("Problem: too few child for event (" + childCount + ")");
        }

        // First child is the Time Slice Segment (TSS) with frame and timestamp (evio v6.0)
        EvioSegment b = (EvioSegment) ev.getChildAt(0).getChildAt(0);
        int[] intData = b.getIntData();
        // get the frame number
        int frameNumber = intData[0];
        // get the time stamp
        long timestamp = ((((long) intData[1]) & 0x00000000ffffffffL) +
                (((long) intData[2]) << 32));

        // Loop through all Aggregation info segments (AIS) which come after TSS.
        // This is ROCs loop
        for (int j = 1; j < childCount; j++) {
            // Create a rcoTimeFrame instance and assign frame number and timestamp
            RocTimeFrameBank rocTimeFrameBank = new RocTimeFrameBank();
            rocTimeFrameBank.setFrameNumber(frameNumber);
            rocTimeFrameBank.setTimeStamp(timestamp);

            EvioBank rocTFB = (EvioBank) ev.getChildAt(j);
            // This must be the ROC ID
            int rocID = rocTFB.getHeader().getTag();
            System.out.println("DDD =====> " + rocID);
            rocTimeFrameBank.setRocID(rocID);

            // Here we get all ROC or streams data (e.g., ROC1, ROC2, etc., aggregated)
            int kids = rocTFB.getChildCount();
            if (kids < 2) {
                throw new Exception("Problem: too few child for TFB (" + childCount + ")");
            }
            List<FADCHit> hits = new ArrayList<>();

            // From here the data is in evio v4.0 format
            // Another level down, each TFB (now evio v4.0) has a Stream Info Bank (SIB) which comes first,
            // followed by data banks.
            //
            // Skip over SIB by starting at 1.
            // Here we get payload (slot) banks
            for (int k = 1; k < kids; k++) {
                EvioBank payloadBank = (EvioBank) rocTFB.getChildAt(k);

                // Get tag of the header which is the payload ID (associated slot number).
                // Note that this number is NOT the VXI slot number
                int payloadId = payloadBank.getHeader().getTag();
                int payloadLength = payloadBank.getHeader().getLength();

                // Ignore the data type (currently the improper value of 0xf).
                // Just get the data as bytes
                byte[] byteData = payloadBank.getRawBytes();

                if (debug) System.out.println("DDD======> Frame = " + frameNumber +
                        ", TS = " + timestamp +
                        ", payload ID = " + payloadId +
                        " length = " + payloadLength);

                if (payloadLength > 3) {
                    hits = parseFADCPayload(timestamp, payloadId, byteData);
                    if (debug) {
                        for (FADCHit h : hits) {
                            System.out.println(h);
                        }
                    }
                    rocTimeFrameBank.addHits(hits);
                }
            }
            banks.add(rocTimeFrameBank);
        }
        return banks;
    }

    @NotNull
    public List<FADCHit> parseFADCPayload(Long frame_time_ns, int payloadId, byte[] ba) {
        List<FADCHit> hits = new ArrayList<>();
        IntBuffer intBuf =
                ByteBuffer.wrap(ba)
                        .order(ByteOrder.BIG_ENDIAN)
                        .asIntBuffer();
        int[] pData = new int[intBuf.remaining()];
        intBuf.get(pData);
        for (int i : pData) {
            int q = (i) & 0x1FFF;
            int channel = (i >>> 13) & 0x000F;
            long v = ((i >>> 17) & 0x3FFF) * 4;
            long ht = frame_time_ns + v;
            hits.add(new FADCHit(1, payloadId, channel, q, ht));
        }
        return hits;
    }

}
