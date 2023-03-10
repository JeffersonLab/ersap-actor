package org.jlab.ersap.actor.sampa.engine;

/**
 * Copyright (c) 2021, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * @author gurjyan on 8/31/22
 * @project ersap-sampa
 */
import org.jlab.epsci.ersap.base.ErsapUtil;
import org.jlab.epsci.ersap.base.error.ErsapException;
import org.jlab.epsci.ersap.engine.Engine;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.util.report.JsonUtils;
import org.jlab.ersap.actor.datatypes.DasDataType;
import org.jlab.ersap.actor.sampa.proc.DasStreamStatistics;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.Set;

public class SampaStatProcEngine implements Engine {
    private static String FEC_COUNT = "fec_count";
    private int fecCount;
    private static final String VERBOSE = "verbose";
    private boolean verbose = false;
    private DasStreamStatistics dasStat;



    @Override
    public EngineData configure(EngineData input) {
        System.out.println("SampaStatProcEngine configure...");

        if (input.getMimeType().equalsIgnoreCase(EngineDataType.JSON.mimeType())) {
            String source = (String) input.getData();
            JSONObject data = new JSONObject(source);
            if (data.has(VERBOSE)) {
                verbose = data.getString(VERBOSE).equalsIgnoreCase("true");
            }
            if (data.has(FEC_COUNT)) {

                fecCount = data.getInt(FEC_COUNT);
                // Each FEC has 2 GBT stream, each having 80 channel data
                int chNum = 80 * fecCount * 2;
                System.out.println("DDDDDDDDDDDD - "+chNum);

                dasStat  = new DasStreamStatistics(chNum);
            }

        }
        return null;
    }

    @Override
    public EngineData execute(EngineData input) {

        ByteBuffer bb = (ByteBuffer)input.getData();
        ByteBuffer[] data = null;
        System.out.println("DDDDDDDDDDDD - ");

        try {
            data = DasDataType.deserialize(bb);
        } catch (ErsapException e) {
            e.printStackTrace();
        }
        if(verbose) {
            dasStat.calculateStats(data);
            dasStat.printStats(System.out, true);
        }
        return input;
    }

    @Override
    public EngineData executeGroup(Set<EngineData> inputs) {
        return inputs.iterator().next();
    }

    @Override
    public Set<EngineDataType> getInputDataTypes() {
        return ErsapUtil.buildDataTypes(EngineDataType.BYTES,
                EngineDataType.JSON);
//        return ErsapUtil.buildDataTypes(SampaDasType.SAMPA_DAS,
//                EngineDataType.JSON);
    }

    @Override
    public Set<EngineDataType> getOutputDataTypes() {
        return ErsapUtil.buildDataTypes(EngineDataType.BYTES);
//        return ErsapUtil.buildDataTypes(SampaDasType.SAMPA_DAS);
    }

    @Override
    public Set<String> getStates() {
        return null;
    }

    @Override
    public String getDescription() {
        return "Test Sampa Stream Engine";
    }

    @Override
    public String getVersion() {
        return "0.1";
    }

    @Override
    public String getAuthor() {
        return "Vardan Gyurjyan";
    }

    @Override
    public void reset() {
    }

    @Override
    public void destroy() {
    }
}
