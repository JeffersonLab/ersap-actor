package org.jlab.ersap.actor.coda.engine;

import org.jlab.epsci.ersap.base.ErsapUtil;
import org.jlab.epsci.ersap.engine.Engine;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.ersap.actor.coda.proc.Awtbc;
import org.jlab.ersap.actor.coda.proc.IStreamItem;
import org.jlab.ersap.actor.coda.proc.fadc.RocTimeSliceBanks;
import org.jlab.ersap.actor.datatypes.JavaObjectType;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Copyright (c) 2021, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * @author gurjyan on 2/9/23
 * {@code} ersap-actor
 * The EventIdentificationEngine class is responsible for identifying events in a single VTP time window.
 * It utilizes sliding window algorithm to define an event based on the FADC hit multiplicity.
 */
public class EventIdentificationEngine implements Engine {
    Awtbc awtbc;
    private static final String SLIDING_WINDOW = "sliding_window";
    private long slidingWindow = 40; // in nanoseconds
    private static final String MULTIPLICITY = "multiplicity";
    private int multiplicity = 2; // number of hits within the sliding window


    @Override
    public EngineData configure(EngineData engineData) {
        if (engineData.getMimeType().equalsIgnoreCase(EngineDataType.JSON.mimeType())) {
            String source = (String) engineData.getData();
            JSONObject data = new JSONObject(source);
            slidingWindow = data.has(SLIDING_WINDOW) ? data.getLong(SLIDING_WINDOW) : 40;
            multiplicity = data.has(MULTIPLICITY) ? data.getInt(MULTIPLICITY) : 2;
        }
        awtbc = new Awtbc(multiplicity, slidingWindow, false);

        return null;
    }

    @Override
    public EngineData execute(EngineData engineData) {
        EngineData out = new EngineData();
        Set<IStreamItem> result = new HashSet<>();
        List<RocTimeSliceBanks> data = (List<RocTimeSliceBanks>) engineData;
        for (RocTimeSliceBanks rsb : data) {
            result.addAll(awtbc.findCluster(rsb.getHits()));
        }
        out.setData(JavaObjectType.JOBJ, result);
        System.out.println("DDD============DDD");
        for (IStreamItem evt : result) {
            System.out.println(evt);
        }
        System.out.println("DDD============DDD");
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
        return null;
    }

    @Override
    public String getVersion() {
        return null;
    }

    @Override
    public String getAuthor() {
        return null;
    }

    @Override
    public void reset() {

    }

    @Override
    public void destroy() {

    }
}
