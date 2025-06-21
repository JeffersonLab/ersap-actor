package org.jlab.ersap.actor.coda.engine;

import org.jlab.coda.jevio.EvioEvent;
import org.jlab.epsci.ersap.base.ErsapUtil;
import org.jlab.epsci.ersap.engine.Engine;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.ersap.actor.coda.proc.EVIO4EvtParser;
import org.jlab.ersap.actor.coda.proc.fadc.RocTimeFrameBank;
import org.jlab.ersap.actor.datatypes.JavaObjectType;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CustomEvio4ParserEngine implements Engine {
private static final String VERBOSE = "verbose";
private String verbose;
    private boolean debug = false;
    EVIO4EvtParser evio4EvtParser;

    @Override
    public EngineData configure(EngineData engineData) {
        if (engineData.getMimeType().equalsIgnoreCase(EngineDataType.JSON.mimeType())) {
            String source = (String) engineData.getData();
            JSONObject data = new JSONObject(source);
            verbose = data.has(VERBOSE) ? data.getString(VERBOSE) : "no";
        }
        if(verbose.trim().equalsIgnoreCase("yes")){
            debug = true;
        }
        evio4EvtParser = new EVIO4EvtParser(debug);

        return null;
    }

    @Override
    public EngineData execute(EngineData engineData) {
        EngineData out = new EngineData();
        List<RocTimeFrameBank> data = new ArrayList<>();
        EvioEvent evt = (EvioEvent)engineData.getData();
        try {
            data.add(evio4EvtParser.parse(evt));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


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
        return "Custom EVIO v4.0 decoder, that is not using EVIO libraries";
    }

    @Override
    public String getVersion() {
        return "0.1";
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
