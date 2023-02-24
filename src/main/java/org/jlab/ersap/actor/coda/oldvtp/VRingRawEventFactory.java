package org.jlab.ersap.actor.coda.oldvtp;

import com.lmax.disruptor.EventFactory;

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
public class VRingRawEventFactory implements EventFactory<VRingRawEvent> {

    @Override
    public VRingRawEvent newInstance() {
        return new VRingRawEvent();
    }
}

