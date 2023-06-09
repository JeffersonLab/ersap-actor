package org.jlab.ersap.actor.coda.engine;

import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.std.services.AbstractEventReaderService;
import org.jlab.epsci.ersap.std.services.EventReaderException;
import org.jlab.ersap.actor.coda.source.et.CodaETReader;
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
 * @project ersap-actor
 */
public class CodaEtSourceEngine extends AbstractEventReaderService<CodaETReader> {
    private static final String ET_NAME = "et_name";
    private String etName;
    @Override
    protected CodaETReader createReader(Path path, JSONObject jsonObject) throws EventReaderException {
        if (jsonObject.has(ET_NAME)) {
            etName = jsonObject.getString(ET_NAME);
        }
        return new CodaETReader(etName);
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
        return null;
    }
}
