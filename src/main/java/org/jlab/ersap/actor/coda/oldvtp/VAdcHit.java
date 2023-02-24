package org.jlab.ersap.actor.coda.oldvtp;

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
public class VAdcHit {
    private int crate;
    private int slot;
    private int channel;
    private int charge;
    private long time;

    public VAdcHit(int crate, int slot, int channel, int charge, long time) {
        this.crate = crate;
        this.slot = slot;
        this.channel = channel;
        this.charge = charge;
        this.time = time;
    }

    public int getCrate() {
        return crate;
    }

    public int getSlot() {
        return slot;
    }

    public int getChannel() {
        return channel;
    }

    public int getCharge() {
        return charge;
    }

    public long getTime() {
        return time;
    }

    @Override
    public String toString() {
        return "AdcHit{" +
                "crate=" + crate +
                ", slot=" + slot +
                ", channel=" + channel +
                ", charge=" + charge +
                ", time=" + time +
                '}';
    }
}

