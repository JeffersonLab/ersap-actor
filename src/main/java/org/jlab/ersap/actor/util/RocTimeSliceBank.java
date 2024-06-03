package org.jlab.ersap.actor.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RocTimeSliceBank {
    private int frmaeNumber;
    private long timeStamp;
    private Map<Integer, List<FADCHit>> rocData = new HashMap<>();

    public void addRocData(int rocId, List<FADCHit> data) {
        rocData.put(rocId, data);
    }

    public int getFrmaeNumber() {
        return frmaeNumber;
    }

    public void setFrmaeNumber(int frmaeNumber) {
        this.frmaeNumber = frmaeNumber;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(long timeStamp) {
        this.timeStamp = timeStamp;
    }

    public Map<Integer, List<FADCHit>> getRocData() {
        return rocData;
    }

}
