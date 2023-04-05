package org.jlab.ersap.actor.sampa.engine;

import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.std.services.AbstractEventReaderService;
import org.jlab.epsci.ersap.std.services.EventReaderException;
import org.jlab.ersap.actor.sampa.EMode;
import org.jlab.ersap.actor.sampa.source.recagg.SFileReaderDecoder;
import org.json.JSONObject;

import java.nio.ByteOrder;
import java.nio.file.Path;

public class SampaDASFileSourceEngine extends AbstractEventReaderService<SFileReaderDecoder> {

    private static final String SMP_FILE = "smpFile";
    @Override
    protected SFileReaderDecoder createReader(Path path, JSONObject opts) throws EventReaderException {
        if (opts.has(SMP_FILE)) {
            String smpFile =opts.getString(SMP_FILE);
            return new SFileReaderDecoder(smpFile, 1, 0, EMode.DAS,8192);
        }
        return null;
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
        return ByteOrder.LITTLE_ENDIAN;
    }

    @Override
    protected Object readEvent(int i) throws EventReaderException {
        return reader.getProcess();
    }

    @Override
    protected EngineDataType getDataType() {
        return EngineDataType.BYTES;
    }
}
