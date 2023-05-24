package org.jlab.ersap.actor.sampa.proc;

import java.util.Map;

/**
 * Copyright (c) 2021, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * @author gurjyan on 5/24/23
 * @project ersap-actor
 */
public class SampaDasGson {
    Map<String, double[]> samples;

    public Map<String, double[]> getSamples() {
        return samples;
    }

    public void setSamples(Map<String, double[]> samples) {
        this.samples = samples;
    }
}
