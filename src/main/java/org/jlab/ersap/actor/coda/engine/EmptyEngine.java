package org.jlab.ersap.actor.coda.engine;

import org.jlab.epsci.ersap.base.ErsapUtil;
import org.jlab.epsci.ersap.engine.Engine;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.ersap.actor.datatypes.JavaObjectType;

import java.util.Set;

public class EmptyEngine implements Engine {
    @Override
    public EngineData configure(EngineData engineData) {
        return null;
    }

    @Override
    public EngineData execute(EngineData engineData) {
        return null;
    }

    @Override
    public EngineData executeGroup(Set<EngineData> set) {
        return null;
    }

    @Override
    public Set<EngineDataType> getInputDataTypes () {
        return ErsapUtil.buildDataTypes(JavaObjectType.JOBJ,
                EngineDataType.JSON);
    }

    @Override
    public Set<EngineDataType> getOutputDataTypes () {
        return ErsapUtil.buildDataTypes(JavaObjectType.JOBJ);
    }

    @Override
    public Set<String> getStates () {
        return null;
    }

    @Override
    public String getDescription () {
        return "fADC data decoder and event identification. EVIO data format";
    }

    @Override
    public String getVersion () {
        return "v1.0";
    }

    @Override
    public String getAuthor () {
        return "gurjyan";
    }

    @Override
    public void reset () {

    }

    @Override
    public void destroy () {
    }
}
