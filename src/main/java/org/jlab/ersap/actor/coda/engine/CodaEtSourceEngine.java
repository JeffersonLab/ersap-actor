package org.jlab.ersap.actor.coda.engine;

import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.std.services.AbstractEventReaderService;
import org.jlab.epsci.ersap.std.services.EventReaderException;
import org.jlab.ersap.actor.coda.source.et.CodaETReader;
import org.jlab.ersap.actor.datatypes.JavaObjectType;
import org.jlab.ersap.actor.util.EConstants;
import org.json.JSONObject;

import java.nio.ByteOrder;
import java.nio.file.Path;

/**
 * Copyright (c) 2021, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * @author gurjyan on 2/9/23
 * {@code } ersap-actor
 */
public class CodaEtSourceEngine extends AbstractEventReaderService<CodaETReader> {
    private static final String ET_NAME = "et_name";
    private String etName = EConstants.udf;
    private static final String ET_STATION_NAME = "et_station";
    private String etStationName = "ersap";
    private static final String ET_PORT = "et_port";
    private static final String MAX_RING_ITEMS = "max_ring_items";

    @Override
    protected CodaETReader createReader(Path path, JSONObject jsonObject) throws EventReaderException {
        if (jsonObject.has(ET_NAME)) {
            etName = jsonObject.getString(ET_NAME);
        } else {
            System.out.println("ERROR: No ET system is defined. Exiting...");
            System.exit(1);
        }
        if (jsonObject.has(ET_STATION_NAME)) {
            etStationName = jsonObject.getString(ET_STATION_NAME);
        }
        int maxRingItems = jsonObject.has(MAX_RING_ITEMS) ? jsonObject.getInt(MAX_RING_ITEMS) : 131072;
        int etPort = jsonObject.has(ET_PORT) ? jsonObject.getInt(ET_PORT) : 23911;

        return new CodaETReader(etName, etPort, etStationName, maxRingItems);
    }

    @Override
    protected void closeReader() {
        reader.close();
    }

    @Override
    protected int readEventCount() throws EventReaderException {
        return reader.getEventCount();
    }

    @Override
    protected ByteOrder readByteOrder() throws EventReaderException {
        return reader.getByteOrder();
    }

    @Override
    protected Object readEvent(int i) throws EventReaderException {
        return reader.nextEvent();
    }

    @Override
    protected EngineDataType getDataType() {
        return JavaObjectType.JOBJ;
    }
}
