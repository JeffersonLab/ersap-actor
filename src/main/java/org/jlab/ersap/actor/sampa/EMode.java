package org.jlab.ersap.actor.sampa;

/**
 * Copyright (c) 2021, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * @author gurjyan on 8/31/22
 * @project ersap-sampa
 */
public enum EMode {
    DSP,
    DAS;

    public boolean isDSP() {return this == DSP;}
    public boolean isDAS() {return this == DAS;}
}
