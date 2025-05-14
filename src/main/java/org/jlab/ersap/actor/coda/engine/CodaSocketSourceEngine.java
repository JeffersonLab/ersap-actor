package org.jlab.ersap.actor.coda.engine;


import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.std.services.AbstractEventReaderService;
import org.jlab.epsci.ersap.std.services.EventReaderException;
import org.jlab.ersap.actor.coda.source.socket.SingleSocketStreamReceiver;
import org.jlab.ersap.actor.coda.source.socket.StreamParameters;
import org.jlab.ersap.actor.datatypes.JavaObjectType;
import org.jlab.ersap.actor.util.AConstants;
import org.json.JSONObject;

import java.nio.ByteOrder;
import java.nio.file.Path;

public class CodaSocketSourceEngine extends AbstractEventReaderService<SingleSocketStreamReceiver> {
    @Override
    protected SingleSocketStreamReceiver createReader(Path path, JSONObject jsonObject) throws EventReaderException {
        //  Default parameters to construct PetStreamReceiver object
        StreamParameters p = new StreamParameters();


        // Get parameters from the ERSAP YAML configuration file
        if (jsonObject.has("streamHost")) {
            p.setHost(jsonObject.getString("streamHost"));
        }
        if (jsonObject.has("streamPort")) {
            p.setPort(jsonObject.getInt("streamPort"));
        }
        if (jsonObject.has("ringBufferSize")) {
            p.setRingBufferSize(jsonObject.getInt("ringBufferSize"));
        }
        if (jsonObject.has("connectionTimeout")) {
            p.setConnectionTimeout(jsonObject.getInt("connectionTimeout"));
        }
        if (jsonObject.has("readTimeout")) {
            p.setReadTimeout(jsonObject.getInt("readTimeout"));
        }
        if (!p.getHost().equals(AConstants.udf)) {
            return new SingleSocketStreamReceiver(p);
        } else {
            throw new EventReaderException("Stream host is undefined");
        }
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