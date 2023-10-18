package org.jlab.ersap.actor.helloworld.engine;

import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.std.services.AbstractEventWriterService;
import org.jlab.epsci.ersap.std.services.EventWriterException;
import org.jlab.ersap.actor.helloworld.sink.PrintStdIO;
import org.json.JSONObject;

import java.nio.file.Path;

public class PrintStdIOEngine extends AbstractEventWriterService<PrintStdIO> {
    @Override
    protected PrintStdIO createWriter(Path path, JSONObject jsonObject) throws EventWriterException {
        return new PrintStdIO();
    }

    @Override
    protected void closeWriter() {

    }

    @Override
    protected void writeEvent(Object o) throws EventWriterException {
       writer.printStdIo(o.toString());
    }

    @Override
    protected EngineDataType getDataType() {
        return EngineDataType.STRING;
    }
}
