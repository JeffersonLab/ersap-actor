package org.jlab.ersap.actor.coda.engine;

import org.jlab.coda.jevio.EvioEvent;
import org.jlab.epsci.ersap.base.ErsapUtil;
import org.jlab.epsci.ersap.engine.Engine;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.ersap.actor.coda.proc.EtEvent;
import org.jlab.ersap.actor.coda.proc.EvioEventParser;
import org.jlab.ersap.actor.datatypes.JavaObjectType;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.Set;

public class CodaHitFinderEngine implements Engine {
    private EvioEventParser parser;
    private static final String STREAMSOURCE = "stream_source";
    private String streamSource;
    private boolean isSourceEt;
    private static final String VERBOSE = "verbose";
    private String verbose;

    @Override
    public EngineData configure(EngineData engineData) {
        if (engineData.getMimeType().equalsIgnoreCase(EngineDataType.JSON.mimeType())) {
            String source = (String) engineData.getData();
            JSONObject data = new JSONObject(source);
            streamSource = data.has(STREAMSOURCE) ? data.getString(STREAMSOURCE) : "et";
            verbose = data.has(VERBOSE) ? data.getString(VERBOSE) : "no";
        }
        if (streamSource.trim().equalsIgnoreCase("et")) {
            isSourceEt = true;
        } else if (streamSource.trim().equalsIgnoreCase("file")) {
            isSourceEt = false;
        }
        if (verbose.trim().equalsIgnoreCase("yes")) {
            parser = new EvioEventParser(true);
        } else {
            parser = new EvioEventParser(false);
        }
        return null;
    }

    @Override
    public EngineData execute(EngineData engineData) {
        if (isSourceEt) {
            return executeETEvent(engineData);
        } else {
            return executeFileEvent(engineData);
        }
    }

    private EngineData executeFileEvent(EngineData engineData) {
        EngineData out = new EngineData();
        EtEvent data;
        // Decoding
        try {
            data = parser.parseFileEvent((EvioEvent) engineData.getData());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        out.setData(JavaObjectType.JOBJ, data);
        return out;
    }

    private EngineData executeETEvent(EngineData engineData) {
        EngineData out = new EngineData();
        EtEvent data;
        // Decoding
        try {
            data = parser.parseEtEvent((ByteBuffer) engineData.getData());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        out.setData(JavaObjectType.JOBJ, data);
        return out;
    }

    @Override
    public EngineData executeGroup(Set<EngineData> set) {
        return null;
    }

    @Override
    public Set<EngineDataType> getInputDataTypes() {
        return ErsapUtil.buildDataTypes(JavaObjectType.JOBJ,
                EngineDataType.JSON);
    }

    @Override
    public Set<EngineDataType> getOutputDataTypes() {
        return ErsapUtil.buildDataTypes(JavaObjectType.JOBJ);
    }

    @Override
    public Set<String> getStates() {
        return null;
    }

    @Override
    public String getDescription() {
        return "fADC data decoder and hit identification. EVIO data format, supports file and ET sources ";
    }

    @Override
    public String getVersion() {
        return "v1.0";
    }

    @Override
    public String getAuthor() {
        return "gurjyan";
    }

    @Override
    public void reset() {

    }

    @Override
    public void destroy() {
    }
}
