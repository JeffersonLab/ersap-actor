package org.jlab.ersap.actor.coda.source.file;

import org.jlab.coda.jevio.EvioEvent;
import org.jlab.coda.jevio.EvioException;
import org.jlab.coda.jevio.EvioReader;
import org.jlab.ersap.actor.util.IASource;

import java.io.File;
import java.io.IOException;
import java.nio.ByteOrder;

/**
 * Copyright (c) 2021, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * @author gurjyan on 2/13/23
 * @project ersap-coda
 */
public class CodaOutputFileReader implements IASource {

    private EvioReader reader;
    private int evCount;
    private int evtIndex = 0; //for evio starts from 1
    private ByteOrder order;

    public CodaOutputFileReader(String fName) {
        try {
            reader = new EvioReader(new File(fName), false, true, false);
            order = reader.getByteOrder();
            evCount = reader.getEventCount();
        } catch (IOException | EvioException e) {
            e.printStackTrace();
        }
        System.out.println("Read in file " + fName + ", got " + evCount + " events");

    }

    public CodaOutputFileReader(File file) {
        try {
            reader = new EvioReader(file, false, true, false);
            order = reader.getByteOrder();
            evCount = reader.getEventCount();
        } catch (IOException | EvioException e) {
            e.printStackTrace();
        }
        System.out.println("Read in file " + file.getName() + ", got " + evCount + " events");

    }

    @Override
    public EvioEvent nextEvent() {
        evtIndex++;
        if (evtIndex <= evCount) {
            try {
                return reader.parseEvent(evtIndex);
            } catch (Exception e) {
                System.out.println(e.getMessage());
                return null;
            }
        }
        return null;
    }

    @Override
    public int getEventCount() {
        return evCount;
    }

    @Override
    public ByteOrder getByteOrder() {
        return order;
    }

    @Override
    public void close(){
        try {
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
