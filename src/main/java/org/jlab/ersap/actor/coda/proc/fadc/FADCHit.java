package org.jlab.ersap.actor.coda.proc.fadc;

import org.jlab.ersap.actor.coda.proc.IStreamItem;

/**
 * Copyright (c) 2021, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * @author gurjyan on 2/13/23
 * {@code } ersap-coda
 */
public class FADCHit implements IStreamItem {

    private final int crate;
    private final int slot;
    private final int channel;
    private final int charge;
    private final long time;

    public FADCHit(int crate, int slot, int channel, int charge, long time) {
        this.crate = crate;
        this.slot = slot;
        this.channel = channel;
        this.charge = charge;
        this.time = time;
    }

    @Override
    public String getName() {
        return crate + "-" + slot + "-" + channel;
    }

    @Override
    public int getId() {
        return (crate * 1000) + (slot * 16) + channel;
    }

    @Override
    public int getValue() {
        return charge;
    }

    public FADCHit withTime(long newTime) {
        return new FADCHit(crate, slot, channel, charge, newTime);
    }

    public int crate() {
        return crate;
    }

    public int slot() {
        return slot;
    }

    public int channel() {
        return channel;
    }

    public int charge() {
        return charge;
    }

    public long time() {
        return time;
    }

    @Override
    public String toString() {
        return "FADCHit{" +
                "crate=" + crate +
                ", slot=" + slot +
                ", channel=" + channel +
                ", charge=" + charge +
                ", time=" + time +
                '}';
    }
}
