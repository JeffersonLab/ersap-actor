package org.jlab.ersap.actor.coda.proc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private long clusterTimeWindow;

    private boolean isExact = false;

    private List<FADCHit> AdaptiveWindow;

    /**
     * Creates an object of the AWTBC algorithm that looks
     * n events happening within a time window t.
     *
     * @param n number of events in the cluster
     * @param t time window of the cluster
     * @param c if set true algorithm will perform exact match to n
     */
    public Awtbc(int n, long t, boolean c) {
        clusterEvents = n;
        clusterTimeWindow = t;
        AdaptiveWindow = new ArrayList<>();
        isExact = c;
    }

    public Set<IStreamItem> findCluster(List<FADCHit> hits) {
        Set<IStreamItem> out = new HashSet<>();
        Set<IStreamItem> tmp = new HashSet<>();
        for (FADCHit hit:hits) {
            if (AdaptiveWindow.size() < clusterEvents) {
                AdaptiveWindow.add(hit);
            } else {
                // loop to remove elements that have larger time than clusterWindowTime

                for (int i = 0; i < AdaptiveWindow.size(); i++) {
                    IStreamItem tItem_0 = AdaptiveWindow.get(i);
                    IStreamItem tItem_1 = AdaptiveWindow.get(i + 1);

                    // We check if ID's of two neighbour items are not the same,
                    // and that the time distance between them are within clusterTimeWindow
                    if ((tItem_0.getId() != tItem_1.getId()) &&
                            (tItem_0.time() - tItem_1.time() <= clusterTimeWindow)) {
                        tmp.add(tItem_0);
                        tmp.add(tItem_1);

                        // If we find a cluster
                        if (tmp.size() == clusterEvents) {
                            // Remove elements from the adaptive window
                            for (int j = 0; j < out.size(); j++) {
                                AdaptiveWindow.remove(j);
                            }
                            out.addAll(tmp);
                            tmp.clear();
                        }
                    }
                }
                // Remove elements from the adaptive window, except the last one.
//                for (int j = 0; j < AdaptiveWindow.size() - 1; j++) {
//                    AdaptiveWindow.remove(j);
//                }
                AdaptiveWindow.clear();
            }
        }
        return out;
    }

}
