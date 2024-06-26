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

    private static final String FRAME_COUNT = "frameCount";
    private int frameCount;
    @Override
    protected SFileReaderDecoder createReader(Path path, JSONObject opts) throws EventReaderException {
        if (opts.has(FRAME_COUNT)) {
             frameCount = opts.getInt(FRAME_COUNT);
//            return new SFileReaderDecoder(smpFile, 1, 0, EMode.DAS,8192);
            return new SFileReaderDecoder(path.toFile().getAbsolutePath(), 1, 4000, EMode.DAS,8192); // vg 10.31.23
        }
        return null;
    }

    @Override
    protected void closeReader() {
       reader.close();
    }

    @Override
    protected int readEventCount() throws EventReaderException {
//        return Integer.MAX_VALUE;
        return frameCount/4000;
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
