package org.jlab.ersap.actor.helloworld.engine;

import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.std.services.AbstractEventWriterService;
import org.jlab.epsci.ersap.std.services.EventWriterException;
import org.jlab.ersap.actor.helloworld.sink.BulkPrintStdIO;
import org.json.JSONObject;

import java.nio.file.Path;

public class BulkPrintStdIOEngine extends AbstractEventWriterService<BulkPrintStdIO> {
    @Override
    protected BulkPrintStdIO createWriter(Path path, JSONObject jsonObject) throws EventWriterException {
        return new BulkPrintStdIO();
    }

    @Override
    protected void closeWriter() {

    }

    @Override
    protected void writeEvent(Object o) throws EventWriterException {
       writer.bprintStdIo(o.toString());
    }

    @Override
    protected EngineDataType getDataType() {
        return EngineDataType.STRING;
    }
}
