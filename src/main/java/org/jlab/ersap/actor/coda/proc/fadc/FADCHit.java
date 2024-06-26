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
public record FADCHit(int crate, int slot, int channel, int charge, long time) implements IStreamItem {

    @Override
    public String getName() {
        return crate + "-" + slot + "-" + channel;
    }

    @Override
    public int getId() {
        return crate + slot + channel;
    }

    @Override
    public int getValue() {
        return charge;
    }
}
