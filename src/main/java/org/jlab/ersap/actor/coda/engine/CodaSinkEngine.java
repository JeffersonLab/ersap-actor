package org.jlab.ersap.actor.coda.engine;

import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.std.services.AbstractEventWriterService;
import org.jlab.epsci.ersap.std.services.EventWriterException;
import org.jlab.ersap.actor.datatypes.JavaObjectType;
import org.json.JSONObject;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

public class CodaSinkEngine extends AbstractEventWriterService<FileWriter> {
    @Override
    protected FileWriter createWriter(Path path, JSONObject jsonObject) throws EventWriterException {
        try {
            return new FileWriter(path.toString());
        } catch (IOException e) {
            throw new EventWriterException(e);
        }
    }

    @Override
    protected void closeWriter() {
        try {
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void writeEvent(Object o) throws EventWriterException {

    }

    @Override
    protected EngineDataType getDataType() {
        return JavaObjectType.JOBJ;
    }
}
