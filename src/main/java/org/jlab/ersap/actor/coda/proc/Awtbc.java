package org.jlab.ersap.actor.coda.proc;

import org.fusesource.jansi.internal.OSInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

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
 * <p>
 * The Adaptive Window Time Based Clustering (AWTBC) algorithm is a novel unsupervised learning
 * approach designed to identify event clusters within a data stream efficiently. Utilizing an
 * adaptive window size and the arrival time of each event, the algorithm effectively groups
 * similar events within a specified time frame. To begin, the right edge of the adaptive sliding
 * window moves, capturing the required number of elements dictated by the input parameters.
 * Next, the algorithm assesses whether the time difference between the elements falls within
 * the predetermined time window. If it does, a cluster is identified; otherwise, elements are
 * removed from the left side of the window until the time difference condition is met.
 * Upon meeting the time criteria, the right edge of the sliding window continues moving to
 * accommodate the necessary number of elements. This iterative process persists, enabling the AWTBC
 * algorithm to adapt dynamically to fluctuating data streams and accurately detect event clusters
 * in real time.
 */
public class Awtbc {

    private int clusterEvents;
    private double clusterTimeWindow;

    private boolean isExact = false;

    private List<IStreamItem> AdaptiveWindow;

    /**
     * Creates an object of the AWTBC algorithm that looks
     * n events happening within a time window t.
     *
     * @param n number of events in the cluster
     * @param t time window of the cluster
     * @param c if set true algorithm will perform exact match to n
     */
    public Awtbc(int n, double t, boolean c) {
        clusterEvents = n;
        clusterTimeWindow = t;
        AdaptiveWindow = new ArrayList<>();
        isExact = c;
    }

    public Set<IStreamItem> findCluster(IStreamItem item) {
        if (AdaptiveWindow.size() < clusterEvents) {
            AdaptiveWindow.add(item);
        }
        if (AdaptiveWindow.size() < clusterEvents) {
            return null;

        } else {
            Set<IStreamItem> out = new HashSet<>();
            // loop to remove elements that have larger time than clusterWindowTime
            for (int i = 0; i < AdaptiveWindow.size(); i++) {
                IStreamItem tItem_0 = AdaptiveWindow.get(i);
                IStreamItem tItem_1 = AdaptiveWindow.get(i + 1);
                if (tItem_0.getTime() - tItem_1.getTime() <= clusterTimeWindow) {
                    out.add(tItem_0);
                    out.add(tItem_1);
                }
            }
            if (isExact) {
                if (out.size() == clusterEvents) {
                    return out;
                } else {
                    return null;
                }
            } else {
                if (out.size() >= clusterEvents) {
                    return out;
                } else {
                    return null;
                }
            }
        }
    }

}