package org.jlab.ersap.actor.helloworld.engine;

import org.jlab.ersap.actor.helloworld.source.AyanStreamReader;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.std.services.AbstractEventReaderService;
import org.jlab.epsci.ersap.std.services.EventReaderException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Path;

public class StreamReaderEngine extends AbstractEventReaderService<AyanStreamReader> {
    private static final String PORT = "port";
    @Override
    protected AyanStreamReader createReader(Path path, JSONObject jsonObject) throws EventReaderException {
        int initialPort = jsonObject.has(PORT) ? jsonObject.getInt(PORT) : 6000;
        return new AyanStreamReader(String.valueOf(initialPort));
       // return null;
    }


    @Override
    protected void closeReader() {
        reader.close();
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
        return reader.readStreamContent();
    }

    @Override
    protected EngineDataType getDataType() {
        return EngineDataType.STRING;
    }
}
