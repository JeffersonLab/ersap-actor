package org.jlab.ersap.actor.coda.proc;

/**
 * Copyright (c) 2021, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * @author gurjyan on 4/25/23
 * @project ersap-actor
 */
public interface IStreamItem {
    public String getName();
    public long time();
    public int getId();
    public int getValue();
}
