package org.jlab.ersap.actor.coda.oldvtp;

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
public class ChargeTime {
    private BigInteger time;
    private int charge;

    ChargeTime(BigInteger time, int charge){
        this.time = time;
        this.charge = charge;
    }
    public BigInteger getTime() {
        return time;
    }

    public int getCharge() {
        return charge;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChargeTime that = (ChargeTime) o;
        return getCharge() == that.getCharge() &&
                getTime().equals(that.getTime());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTime(), getCharge());
    }

    @Override
    public String toString() {
        return "ChargeTime{" +
                "time=" + time +
                ", charge=" + charge +
                '}';
    }
}

