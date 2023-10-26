package org.jlab.ersap.actor.helloworld.engine;

import org.jlab.ersap.actor.helloworld.source.AyanFileReader;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.std.services.AbstractEventReaderService;
import org.jlab.epsci.ersap.std.services.EventReaderException;
import org.json.JSONObject;

import java.io.IOException;
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
        return ByteOrder.BIG_ENDIAN;
    }

    @Override
    protected Object readEvent(int i) throws EventReaderException {
        return reader.readFileContent();
    }

    @Override
    protected EngineDataType getDataType() {
        return EngineDataType.STRING;
    }
}
