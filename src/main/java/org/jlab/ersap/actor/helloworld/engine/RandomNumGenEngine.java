package org.jlab.ersap.actor.helloworld.engine;

import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.std.services.AbstractEventReaderService;
import org.jlab.epsci.ersap.std.services.EventReaderException;
import org.jlab.ersap.actor.helloworld.source.RandNumGen;
import org.json.JSONObject;

import java.nio.ByteOrder;
import java.nio.file.Path;

public class RandomNumGenEngine extends AbstractEventReaderService<RandNumGen> {
    @Override
    protected RandNumGen createReader(Path path, JSONObject jsonObject) throws EventReaderException {
        return new RandNumGen();
    }

    @Override
    protected void closeReader() {

    }

    @Override
    protected int readEventCount() throws EventReaderException {
        return Integer.MAX_VALUE;
    }

    @Override
    protected ByteOrder readByteOrder() throws EventReaderException {
        return ByteOrder.BIG_ENDIAN;
    }

    @Override
    protected Object readEvent(int i) throws EventReaderException {
        return reader.generate();
    }

    @Override
    protected EngineDataType getDataType() {
        return EngineDataType.SINT64;
    }
}
