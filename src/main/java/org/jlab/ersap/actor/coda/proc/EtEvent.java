package org.jlab.ersap.actor.coda.proc;

import java.util.ArrayList;
import java.util.List;

public class EtEvent {
    private List<List<RocTimeFrameBank>> timeFrames = new ArrayList<>();

    public List<List<RocTimeFrameBank>> getTimeFrames() {
        return timeFrames;
    }

    public void addTimeFrame(List<RocTimeFrameBank> timeFrame) {
        this.timeFrames.add(timeFrame);
    }
}
