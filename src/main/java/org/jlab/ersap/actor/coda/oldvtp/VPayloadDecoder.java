package org.jlab.ersap.actor.coda.oldvtp;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
public class VPayloadDecoder {
    private final VAdcHitMap evt;
    private final List<Integer> pData;

    private int prescale;
    private static int PVALUE = 50;

    public VPayloadDecoder() {
        evt = new VAdcHitMap(2000000);
        pData = new ArrayList<>();
        prescale = PVALUE;
    }

    public void decode(Long frame_time_ns, ByteBuffer buf, int s1, int s2) {
        pData.clear();
        evt.reset();

        buf.rewind();
        while (buf.hasRemaining()) {
            pData.add(buf.getInt());
        }
        buf.clear();
        corePayloadDecoder(frame_time_ns, pData, s1);
        corePayloadDecoder(frame_time_ns, pData, s2);
    }

    public void decode(Long frame_time_ns, ByteBuffer buf) {
        // analyze every prescale event
//        if ((prescale -= 1) > 0) return;
//        prescale = PVALUE;

        pData.clear();
        evt.reset();

        buf.rewind();
        while (buf.hasRemaining()) {
            pData.add(buf.getInt());
        }
        buf.clear();
        corePayloadDecoder(frame_time_ns, pData, 0);
//        dump(evt.getEvList()); // dump entire frame
//        eventIdentificationAndWriting(4L, 1); // print coincidences within 4 ns window, multiplicity 1
        slidingWindowIdentification(1L,16l,4);
    }


    public ByteBuffer getEvt() {
        return evt.getEvt();
    }

    private void corePayloadDecoder(Long frame_time_ns,
                                    List<Integer> pData, int pIndex) {
        if (!pData.isEmpty()) {
            if ((pData.get(pIndex) & 0x8FFF8000) == 0x80000000) {
                for (int j = pIndex + 1; j < pIndex + 9; j++) {
                    int vl = pData.get(j);
                    int slot_ind = (vl >> 0) & 0xFFFF;
                    int slot_len = (vl >> 16) & 0xFFFF;
                    if (slot_len > 0) {
                        int type = 0x0;
                        int crate = -1;
                        int slot = -1;
                        for (int jj = 0; jj < slot_len; jj++) {
                            int val = pData.get(slot_ind + pIndex + jj);
                            if ((val & 0x80000000) == 0x80000000) {
                                type = (val >> 15) & 0xFFFF;
                                crate = (val >> 8) & 0x007F;
                                slot = (val >> 0) & 0x001F;
                            } else if (type == 0x0001) { // FADC hit type
                                int q = (val >> 0) & 0x1FFF;
                                int channel = (val >> 13) & 0x000F;
                                long v = ((val >> 17) & 0x3FFF) * 4;
                                long ht = frame_time_ns + v;
                                evt.add(ht, crate, slot, channel, q);
                            }
                        }
                    }
                }
            }
        }
    }

    public void dump(List<VAdcHit> hit_map) {
        System.out.println("\n========================================= ");
        if (hit_map.size() < 0) {
            System.out.println("\nWarning: hit-map is inconsistent");
        } else {
            for (VAdcHit hit : hit_map) {
                System.out.println(hit);
            }
        }
    }

    public void eventIdentificationAndWriting(long width, int level) {
        List<VAdcHit> tmp_res = new ArrayList<>();
        if (evt.getEvtSize() > 0) {
            long leadingEdge = evt.getEvList().get(0).getTime();
            for (VAdcHit hit : evt.getEvList()) {
                if (hit.getTime() > leadingEdge + width) {
                    leadingEdge = hit.getTime();

                    // write event to hipo file
                    if (tmp_res.size() >= level) {
                        VTP1StreamReceiverDecoder.hipoFile.evtWrite(tmp_res);
                        VTP1StreamReceiverDecoder.ebEvents++;
//                        dump(tmp_res);
                    }
                    tmp_res.clear();
                } else {
                    tmp_res.add(hit);
                }
            }
        }
    }

    /**
     *
     * @param slidingStep sliding step in ns.
     * @param windowSize sliding window size in ns
     * @param hitMultiplicity minimum number of hits within the sliding window.
     */
    public void slidingWindowIdentification(long slidingStep, long windowSize, int hitMultiplicity) {
        long leadingTime;
        long l1;
        long l2;
        long l3;
        long l4;
        long l5;
        List<VAdcHit> list1 = new ArrayList<>();
        List<VAdcHit> list2 = new ArrayList<>();
        List<VAdcHit> list3 = new ArrayList<>();
        List<VAdcHit> list4 = new ArrayList<>();
        List<VAdcHit> list5 = new ArrayList<>();
        List<Integer> sizeList;

        if (evt.getEvtSize() > 0) {
            leadingTime = evt.getEvList().get(0).getTime();
            l1 = leadingTime;
            l2 = leadingTime + slidingStep;
            l3 = leadingTime + (slidingStep * 2L);
            l4 = leadingTime + (slidingStep * 3L);
            l5 = leadingTime + (slidingStep * 4L);

            for (VAdcHit hit : evt.getEvList()) {
                if ((hit.getTime() >= l1) && (hit.getTime() < (l1 + windowSize))) {
                    list1.add(hit);
                } else if ((hit.getTime() >= l2) && (hit.getTime() < (l2 + windowSize))) {
                    list2.add(hit);
                } else if ((hit.getTime() >= l3) && (hit.getTime() < (l3 + windowSize))) {
                    list3.add(hit);
                } else if ((hit.getTime() >= l4) && (hit.getTime() < (l4 + windowSize))) {
                    list4.add(hit);
                } else if ((hit.getTime() >= l5) && (hit.getTime() < (l5 + windowSize))) {
                    list5.add(hit);
                } else if (hit.getTime() >= (l5 + windowSize)) {
                    // find the max size list, and define as an event
                    sizeList = Arrays.asList(list1.size(), list2.size(), list3.size(), list4.size(), list5.size());
                    Integer max = Collections.max(sizeList);

                    // write event
                    if(list1.size() == max) {
                        if (list1.size() >= hitMultiplicity) {
                            VTP1StreamReceiverDecoder.hipoFile.evtWrite(list1);
                            VTP1StreamReceiverDecoder.ebEvents++;
                        }
                    } else if(list2.size() == max) {
                        if (list2.size() >= hitMultiplicity) {
                            VTP1StreamReceiverDecoder.hipoFile.evtWrite(list2);
                            VTP1StreamReceiverDecoder.ebEvents++;
                        }
                    } else if(list3.size() == max) {
                        if (list3.size() >= hitMultiplicity) {
                            VTP1StreamReceiverDecoder.hipoFile.evtWrite(list3);
                            VTP1StreamReceiverDecoder.ebEvents++;
                        }
                    } else if(list4.size() == max) {
                        if (list4.size() >= hitMultiplicity) {
                            VTP1StreamReceiverDecoder.hipoFile.evtWrite(list4);
                            VTP1StreamReceiverDecoder.ebEvents++;
                        }
                    } else if(list5.size() == max) {
                        if (list5.size() >= hitMultiplicity) {
                            VTP1StreamReceiverDecoder.hipoFile.evtWrite(list5);
                            VTP1StreamReceiverDecoder.ebEvents++;
                        }
                    }

                    // redefine the leading time
                    leadingTime = l5 + slidingStep;
                    l1 = leadingTime;
                    l2 = leadingTime + slidingStep;
                    l3 = leadingTime + (slidingStep * 2L);
                    l4 = leadingTime + (slidingStep * 3L);
                    l5 = leadingTime + (slidingStep * 4L);

                    // reset lists
                    list1.clear();
                    list2.clear();
                    list3.clear();
                    list4.clear();
                    list5.clear();
//                    sizeList.clear();
                }
            }
        }
    }
}

