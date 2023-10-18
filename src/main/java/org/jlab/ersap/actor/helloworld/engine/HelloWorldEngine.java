package org.jlab.ersap.actor.helloworld.engine;

import org.jlab.epsci.ersap.base.ErsapUtil;
import org.jlab.epsci.ersap.engine.Engine;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.ersap.actor.helloworld.proc.HelloWorld;

import java.util.Set;

public class HelloWorldEngine implements Engine {

    private HelloWorld hw;

    @Override
    public EngineData configure(EngineData engineData) {
        hw = new HelloWorld();
        return null;
    }

    @Override
    public EngineData execute(EngineData input) {
        if (input.getMimeType().equalsIgnoreCase(EngineDataType.SINT64.mimeType())) {
            EngineData out = new EngineData();
            out.setData(EngineDataType.STRING,hw.defineHelloWorld((Integer)input.getData()));
            return out;
        } else {
            return input;
        }
    }

    @Override
    public EngineData executeGroup(Set<EngineData> set) {
        return null;
    }

    @Override
    public Set<EngineDataType> getInputDataTypes() {
        return ErsapUtil.buildDataTypes(EngineDataType.SINT64,
                EngineDataType.JSON);
    }

    @Override
    public Set<EngineDataType> getOutputDataTypes() {
        return ErsapUtil.buildDataTypes(EngineDataType.STRING);
    }

    @Override
    public Set<String> getStates() {
        return null;
    }

    @Override
    public String getDescription() {
        return "Simple Hello World actor engine";
    }

    @Override
    public String getVersion() {
        return "v0.1";
    }

    @Override
    public String getAuthor() {
        return "vg";
    }

    @Override
    public void reset() {
    }

    @Override
    public void destroy() {

    }
}
