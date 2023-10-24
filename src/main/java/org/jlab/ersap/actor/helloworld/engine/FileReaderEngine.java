package org.jlab.ersap.actor.helloworld.engine;

import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.std.services.AbstractEventReaderService;
import org.jlab.epsci.ersap.std.services.EventReaderException;
import org.json.JSONObject;

import java.nio.ByteOrder;
import java.nio.file.Path;

public class FileReaderEngine extends AbstractEventReaderService<AyanFileReader> {
    private static final String IN_FILE = "inputFile";
    @Override
    protected AyanFileReader createReader(Path path, JSONObject jsonObject) throws EventReaderException {
        if (jsonObject.has(IN_FILE)) {
            String inFile = jsonObject.getString(IN_FILE);
            return new AyanFileReader(inFile);
        }
        return null;
    }


    @Override
    protected void closeReader() {

    }

    @Override
    protected int readEventCount() throws EventReaderException {
        return 0;
    }

    @Override
    protected ByteOrder readByteOrder() throws EventReaderException {
        return null;
    }

    @Override
    protected Object readEvent(int i) throws EventReaderException {
        return null;
    }

    @Override
    protected EngineDataType getDataType() {
        return null;
    }
}
