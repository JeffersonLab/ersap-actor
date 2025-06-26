package org.jlab.ersap.actor.coda.proc;

import java.util.ArrayList;
import java.util.List;

public class RocTimeFrameBank {
    private int rocID;
    private int frameNumber;
    private long timeStamp;
    private List<FADCHit> hits = new ArrayList<>();

    public int getFrameNumber() {
        return frameNumber;
    }

    public void setFrameNumber(int frameNumber) {
        this.frameNumber = frameNumber;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(long timeStamp) {
        this.timeStamp = timeStamp;
    }

    public List<FADCHit> getHits() {
        return hits;
    }

    public void setHits(List<FADCHit> hits) {
        this.hits = hits;
    }
    public void addHits(List<FADCHit> hits) {
        this.hits.addAll(hits);
    }

    public void addHit(FADCHit hit) {
        hits.add(hit);
    }

    public int getRocID() {
        return rocID;
    }

    public void setRocID(int rocID) {
        this.rocID = rocID;
    }
}
