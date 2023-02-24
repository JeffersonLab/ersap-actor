package org.jlab.ersap.actor.coda.oldvtp;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.jlab.ersap.actor.coda.oldvtp.EUtil.cloneByteBuffer;

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
public class VAdcHitMap {
    private ByteBuffer evt;
    private List<VAdcHit> ev_list = new ArrayList<>();
    private int evtSize;

    public VAdcHitMap(int size) {
        evt = ByteBuffer.allocate(size);
    }

    public void reset() {
        evt.clear();
        evtSize = 0;
        ev_list.clear();
    }


    public void add(long time, int crate, int slot, int channel, int charge) {
        evt.putLong(time);
        evt.putInt(crate);
        evt.putInt(slot);
        evt.putInt(channel);
        evt.putInt(charge);
        evtSize++;
        ev_list.add(new VAdcHit(crate, slot, channel, charge, time));
    }

    public ByteBuffer getEvt(){
        return cloneByteBuffer(evt);
    }

    public List<VAdcHit> getEvList() {
        return ev_list;
    }


    public int getEvtSize(){
        return evtSize;
    }
}

