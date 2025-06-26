package org.jlab.ersap.actor.coda.engine;

import org.jlab.epsci.ersap.base.ErsapUtil;
import org.jlab.epsci.ersap.engine.Engine;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.ersap.actor.coda.proc.EtEvent;
import org.jlab.ersap.actor.coda.proc.FADCHit;
import org.jlab.ersap.actor.coda.proc.LiveHistogram;
import org.jlab.ersap.actor.coda.proc.RocTimeFrameBank;
import org.jlab.ersap.actor.datatypes.JavaObjectType;
import org.jlab.utils.JsonUtils;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

public class MultiChannelDigitizerDisplay implements Engine {
    private static String FRAME_TITLE = "frame_title";
    private String frameTitle = "ERSAP";
    private static String FRAME_WIDTH = "frame_width";
    private int frameWidth = 1200;
    private static String FRAME_HEIGHT = "frame_height";
    private int frameHeight = 1200;
    private static String HIST_BINS = "hist_bins";
    private int histBins = 100;
    private static String HIST_MIN = "hist_min";
    private double histMin = 0;
    private static String HIST_MAX = "hist_max";
    private double histMax = 8000;
    private static String ROC_ID = "roc_id";
    private int rocId = 1;
    private static String SLOT = "slot";
    private int slot = 1;

    private LiveHistogram liveHist;

    @Override
    public EngineData configure(EngineData engineData) {
        if (engineData.getMimeType().equalsIgnoreCase(EngineDataType.JSON.mimeType())) {
            String source = (String) engineData.getData();
            JSONObject opts = new JSONObject(source);
            if (opts.has(FRAME_TITLE)) {
                frameTitle = opts.getString(FRAME_TITLE);
            }
            if (opts.has(FRAME_WIDTH)) {
                frameWidth = opts.getInt(FRAME_WIDTH);
            }
            if (opts.has(FRAME_HEIGHT)) {
                frameHeight = opts.getInt(FRAME_HEIGHT);
            }
            if (opts.has(HIST_BINS)) {
                histBins = opts.getInt(HIST_BINS);
            }
            if (opts.has(HIST_MIN)) {
                histMin = opts.getDouble(HIST_MIN);
            }
            if (opts.has(HIST_MAX)) {
                histMax = opts.getDouble(HIST_MAX);
            }
            if (opts.has(SLOT)) {
                slot = opts.getInt(SLOT);
            }
            if (opts.has(ROC_ID)) {
                rocId = opts.getInt(ROC_ID);
            }

            List<String> histTitles = new ArrayList<>();
            for(int i=0;i<16;i++){
                histTitles.add(rocId+"-"+slot+"-"+i);
            }

            liveHist = new LiveHistogram(frameTitle, histTitles, 4,
                    frameWidth, frameHeight, histBins, histMin, histMax);
        }
        return null;
    }

    @Override
    public EngineData execute(EngineData engineData) {
        EtEvent data = (EtEvent) engineData.getData();
        for(List<RocTimeFrameBank> rtf: data.getTimeFrames()){
            for(RocTimeFrameBank tb: rtf){
                System.out.println("DDD =====> rocID = "+tb.getRocID()+" "+rocId + tb.getHits().isEmpty());
//                if(tb.getRocID() == rocId) {
                    for (FADCHit hit : tb.getHits()) {
                        System.out.printf("DDD => " + hit);
                        liveHist.update(hit.getName(), hit);
                    }
//                }
            }
        }
        return engineData;
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
        return "CODA EVIO data histogram actor";
    }

    @Override
    public String getVersion() {
        return "v0.1";
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
