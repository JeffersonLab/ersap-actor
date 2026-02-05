package org.jlab.ersap.actor.coda.engine;

import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.std.services.AbstractEventReaderService;
import org.jlab.epsci.ersap.std.services.EventReaderException;
import org.jlab.ersap.actor.coda.source.UniAdapter;
import org.json.JSONObject;

import java.nio.ByteOrder;
import java.nio.file.Path;

public class UniAdapterSourceEngine extends AbstractEventReaderService<UniAdapter> {
    @Override
    protected UniAdapter createReader(Path path, JSONObject jsonObject) throws EventReaderException {
        return new UniAdapter();
    }

    @Override
    protected void closeReader() {
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
        return EngineDataType.SINT32;
    }
}
