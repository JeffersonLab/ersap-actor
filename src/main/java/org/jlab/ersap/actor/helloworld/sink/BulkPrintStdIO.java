package org.jlab.ersap.actor.helloworld.sink;

import java.util.ArrayList;
public class BulkPrintStdIO {
    private ArrayList <String> dataObtained = new ArrayList<>();
    
    public void bprintStdIo (String data) {
        dataObtained.add(data);
        if (dataObtained.size() == 2){
            System.out.println(dataObtained);
            dataObtained.clear();
        }
    }
}

