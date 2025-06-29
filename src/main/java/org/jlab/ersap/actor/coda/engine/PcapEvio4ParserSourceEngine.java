package org.jlab.ersap.actor.coda.engine;

import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.std.services.AbstractEventReaderService;
import org.jlab.epsci.ersap.std.services.EventReaderException;
import org.jlab.ersap.actor.coda.proc.CustomEvioEventParser;
import org.jlab.ersap.actor.datatypes.JavaObjectType;
import org.json.JSONObject;

import java.nio.ByteOrder;
import java.nio.file.Path;

public class PcapEvio4ParserSourceEngine extends AbstractEventReaderService<CustomEvioEventParser> {
    private static final String VERBOSE = "verbose";
    private static final String FIFO_CAPACITY = "fifo_capacity";
    private boolean debug = false;
    public CustomEvioEventParser customEvioEventParser;


    @Override
    protected CustomEvioEventParser createReader(Path path, JSONObject jsonObject) throws EventReaderException {
        String verbose = jsonObject.has(VERBOSE) ? jsonObject.getString(VERBOSE) : "no";
        int fifoCapacity = jsonObject.has(FIFO_CAPACITY) ? jsonObject.getInt(FIFO_CAPACITY) : 131072;

        if (verbose.trim().equalsIgnoreCase("yes")) {
            debug = true;
        }
        System.out.println("DDD ===========> "+path.toAbsolutePath().toString());
        return customEvioEventParser = new CustomEvioEventParser(path.toAbsolutePath().toString(), fifoCapacity, debug);
    }

    @Override
    protected void closeReader() {
      reader.close();
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
        return JavaObjectType.JOBJ;
    }


}
