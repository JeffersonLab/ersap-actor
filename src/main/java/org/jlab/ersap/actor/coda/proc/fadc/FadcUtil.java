package org.jlab.ersap.actor.coda.proc.fadc;

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

public class FadcUtil {
    public static ByteOrder evioDataByteOrder = ByteOrder.BIG_ENDIAN;

    @NotNull
    public static List<RocTimeSliceBank> parseEtEvent(ByteBuffer buf) throws Exception {
        EvioReader r = new EvioReader(buf);
        System.out.println("DDD======= "+r.getEvioVersion());
        System.out.println("DDD======= "+r.getEventCount());
        System.out.println("DDD======= "+r.getPath());
        System.out.println("DDD======= "+r.getBlockCount());

        List<RocTimeSliceBank> banks = new ArrayList<>();
//        for (int i = 0; i < r.getEventCount(); i++) {
//            EvioEvent event = r.parseNextEvent();
//            evioDataByteOrder = r.getByteOrder();
//            RocTimeSliceBank rtsb = parseRocTimeSliceBank(event);
//            if (!rtsb.getHits().isEmpty()) {
//                banks.add(rtsb);
//            }
//        }
        return banks;
    }

    public static RocTimeSliceBank parseRocTimeSliceBank(EvioEvent ev) throws Exception{

        int evTag = ev.getHeader().getTag();
//        System.out.println("DDD "+Integer.toHexString(evTag));
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

//        if (evTag == 0xff60) {
//            System.out.println("Found built streaming event");
//        }

        // Go one level down ->
        int childCount = ev.getChildCount();
        if (childCount < 2) {
            throw new Exception("Problem: too few child for event (" + childCount + ")");
        }
//                System.out.println("Event has " + childCount + " child structures");


        // First bank is Time Info Bank (TSS) with frame and timestamp
        EvioSegment b = (EvioSegment) ev.getChildAt(0).getChildAt(0);
        int[] intData = b.getIntData();
        int frame = intData[0];
        long timestamp = ((((long) intData[1]) & 0x00000000ffffffffL) +
                (((long) intData[2]) << 32));
//        System.out.println("  Frame = " + frame + ", TS = " + timestamp);

        RocTimeSliceBank rocTimeSliceBank = new RocTimeSliceBank();
        rocTimeSliceBank.setFrameNumber(frame);
        rocTimeSliceBank.setTimeStamp(timestamp);

        // Loop through all Aggregation info segments (AIS) which come after TSS

        for (int j = 1; j < childCount; j++) {
            // ROC Time SLice Bank
            EvioBank rocTSB = (EvioBank) ev.getChildAt(j);
            int kids = rocTSB.getChildCount();
            if (kids < 2) {
                throw new Exception("Problem: too few child for TSB (" + childCount + ")");
            }
            List<FADCHit> hits = new ArrayList<>();
            // Another level down, each TSB has a Stream Info Bank (SIB) which comes first,
            // followed by data banks

            // Skip over SIB by starting at 1
            for (int k = 1; k < kids; k++) {
                EvioBank dataBank = (EvioBank) rocTSB.getChildAt(k);
                // Ignore the data type (currently the improper value of 0xf).
                // Just get the data as bytes
                int payloadId = dataBank.getHeader().getTag();
                int payloadLength = dataBank.getHeader().getLength();


                byte[] byteData = dataBank.getRawBytes();
                if(payloadLength > 3) {
//                    System.out.println("payload ID = " + payloadId + " length = " + payloadLength + " byteData_length = " + byteData.length);
                    hits = FadcUtil.parseFADCPayload(timestamp, payloadId, byteData);
//                System.out.println("DDD ------------ "+k);
//                for (FADCHit h : hits) {
//                    System.out.println(h);
//                }
//                System.out.println("DDD ------------ "+k);
                }
            }
            rocTimeSliceBank.setHits(hits);
        }
        return rocTimeSliceBank;
    }

    @NotNull
    public static List<FADCHit> parseFADCPayload(Long frame_time_ns, int payloadId, byte[] ba) {
        List<FADCHit> hits = new ArrayList<>();
        IntBuffer intBuf =
                ByteBuffer.wrap(ba)
                        .order(ByteOrder.BIG_ENDIAN)
                        .asIntBuffer();
        int[] pData = new int[intBuf.remaining()];
        intBuf.get(pData);
        for (int i : pData) {
            int q = (i >> 0) & 0x1FFF;
            int channel = (i >> 13) & 0x000F;
            long v = ((i >> 17) & 0x3FFF) * 4;
            long ht = frame_time_ns + v;
            hits.add(new FADCHit(1, payloadId, channel, q, ht));
        }
        return hits;
    }

}
