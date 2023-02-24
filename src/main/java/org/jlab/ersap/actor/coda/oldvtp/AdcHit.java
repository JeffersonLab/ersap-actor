package org.jlab.ersap.actor.coda.oldvtp;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.Objects;

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
public class AdcHit implements Serializable {

    private int crate;
    private int slot;
    private int channel;
    private int q;
    private BigInteger time;

    public AdcHit(int crate, int slot, int channel, int q, BigInteger time) {
        this.crate = crate;
        this.slot = slot;
        this.channel = channel;
        this.q = q;
        this.time = time;
    }

    public AdcHit() {
    }

    public int getCrate() {
        return crate;
    }

    public void setCrate(int crate) {
        this.crate = crate;
    }

    public int getSlot() {
        return slot;
    }

    public void setSlot(int slot) {
        this.slot = slot;
    }

    public int getChannel() {
        return channel;
    }

    public void setChannel(int channel) {
        this.channel = channel;
    }

    public int getQ() {
        return q;
    }

    public void setQ(int q) {
        this.q = q;
    }

    public BigInteger getTime() {
        return time;
    }

    public void setTime(BigInteger time) {
        this.time = time;
    }


    public void reset(){
        crate = 0;
        slot = 0;
        channel = 0;
        q = 0;
        time = BigInteger.valueOf(0);
    }
    @Override
    public String toString() {
        return "AdcHit{" +
                "crate=" + crate +
                ", slot=" + slot +
                ", channel=" + channel +
                ", q=" + q +
                ", time=" + time +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AdcHit adcHit = (AdcHit) o;
        return getCrate() == adcHit.getCrate() &&
                getSlot() == adcHit.getSlot() &&
                getChannel() == adcHit.getChannel() &&
                getQ() == adcHit.getQ() &&
                getTime().equals(adcHit.getTime());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getCrate(), getSlot(), getChannel(), getQ(), getTime());
    }
}

