package org.jlab.ersap.actor.coda.source.socket;

import org.jlab.ersap.actor.util.IASource;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class MultiSocketStreamReceiver implements IASource {
    private int numStreamReceivers;
    private List<SingleSocketStreamReceiver> streamReceivers;

    public MultiSocketStreamReceiver(StreamParameters[] ps) {
        numStreamReceivers = ps.length;
        streamReceivers = new ArrayList<>();
        for (int i = 0; i < numStreamReceivers; i++) {
            SingleSocketStreamReceiver streamReceiver = new SingleSocketStreamReceiver(ps[i]);
            streamReceivers.add(streamReceiver);
        }
    }

    @Override
    public Object nextEvent() {
        Object[] res = new Object[numStreamReceivers];
        // Simple object aggregation
        for (int i = 0; i < numStreamReceivers; i++) {
            res[i] = streamReceivers.get(i).nextEvent();
        }
        return res;
    }

    @Override
    public int getEventCount() {
        return Integer.MAX_VALUE;
    }

    @Override
    public ByteOrder getByteOrder() {
        return null;
    }

    @Override
    public void close() {

    }
}