package org.jlab.ersap.actor.coda.engine;

import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.std.services.AbstractEventWriterService;
import org.jlab.epsci.ersap.std.services.EventWriterException;
import org.jlab.ersap.actor.coda.proc.LiveHistogram;
import org.jlab.ersap.actor.coda.proc.fadc.FADCHit;
import org.jlab.ersap.actor.coda.proc.fadc.RocTimeSliceBanks;
import org.jlab.ersap.actor.datatypes.JavaObjectType;
import org.json.JSONObject;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Copyright (c) 2021, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * @author gurjyan on 2/9/23
 * {@code } ersap-actor
 */
public class CodaFileSinkEngine extends AbstractEventWriterService<FileWriter> {
    private static String FRAME_TITLE = "frame_title";
    private String frameTitle = "ERSAP";
    private static String FRAME_WIDTH = "frame_width";
    private int frameWidth = 1400;
    private static String FRAME_HEIGHT = "frame_height";
    private int frameHeight = 1200;
    private static String HIST_TITLES = "hist_titles";
    private ArrayList<String> histTitles = new ArrayList<>(Arrays.asList("1-2-0", "1-2-1", "1-2-2", "1-2-1&2"));;
    private static String HIST_TITLES2 = "hist_titles2";
    private ArrayList<String> histTitles2;
    private static String HIST_BINS = "hist_bins";
    private int histBins = 100;
    private static String HIST_MIN = "hist_min";
    private double histMin = 0;
    private static String HIST_MAX = "hist_max";
    private double histMax = 8000;
    private static String GRID_SIZE = "grid_size";
    private int gridSize = 2;
    private static String SCATTER_RESET = "scatter_reset";
    private boolean scatterReset = true;

    private LiveHistogram liveHist;

    private List<FADCHit> coinsTimes = new ArrayList<>();

    @Override
    protected FileWriter createWriter(Path file, JSONObject opts) throws EventWriterException {
        if (opts.has(FRAME_TITLE)) {
            frameTitle = opts.getString(FRAME_TITLE);
        }
        if (opts.has(FRAME_WIDTH)) {
            frameWidth = opts.getInt(FRAME_WIDTH);
        }
        if (opts.has(FRAME_HEIGHT)) {
            frameHeight = opts.getInt(FRAME_HEIGHT);
        }
        if (opts.has(HIST_TITLES)) {
            histTitles = new ArrayList<>();
            String ht = opts.getString(HIST_TITLES);
            StringTokenizer st = new StringTokenizer(ht, ",");
            while (st.hasMoreTokens()) {
                histTitles.add(st.nextToken().trim());
            }
        }
        if (opts.has(HIST_TITLES2)) {
            histTitles2 = new ArrayList<>();
            String ht = opts.getString(HIST_TITLES2);
            StringTokenizer st = new StringTokenizer(ht, ",");
            while (st.hasMoreTokens()) {
                histTitles2.add(st.nextToken().trim());
            }
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
        if (opts.has(GRID_SIZE)) {
            gridSize = opts.getInt(GRID_SIZE);
        }

        if (opts.has(SCATTER_RESET)) {
            scatterReset = true;
        }

        liveHist = new LiveHistogram(frameTitle, histTitles, histTitles2, gridSize,
                frameWidth, frameHeight, histBins, histMin, histMax);

        try {
            return new FileWriter(file.toString());
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
    protected void writeEvent(Object event) throws EventWriterException {
        List<RocTimeSliceBanks> banks = (List<RocTimeSliceBanks>)event;
        coinsTimes.clear();
        if (!banks.isEmpty()) {
            if (scatterReset) liveHist.resetScatter();
            for (RocTimeSliceBanks bank : banks) {
                List<FADCHit> hits = bank.getHits();
                System.out.println();
                System.out.println("DDD ------------ Frame = "+bank.getFrameNumber());

                for (FADCHit hit : hits) {
                    System.out.println(hit);
                    liveHist.update(hit.getName(),hit);
                    if(hit.getName().trim().equals("1-2-0")){
                        coinsTimes.add(hit);
                    }
                }
                // Coincidence
                for (FADCHit hit : hits) {
                    if(hit.getName().trim().equals("1-2-1")){
                        for (FADCHit h : coinsTimes){
                            if(hit.time() >= h.time()-20 && hit.time() <= h.time()+20) {
                                liveHist.update("1-2-1&2",new FADCHit(7,7,7,h.charge()+hit.charge(), hit.time()));
                            }
                        }
                    }
                }
                System.out.println("DDD ------------ Time  = "+bank.getTimeStamp());
            }
        }

//        List<VAdcHit> h = (List<VAdcHit>) event;
//        if(!h.isEmpty()) {
//            if (scatterReset) liveHist.resetScatter();
//            for (VAdcHit v : h) {
//                liveHist.update(v.getName().trim(), v);
//            }
//        }

    }

    @Override
    protected EngineDataType getDataType() {
        return JavaObjectType.JOBJ;
    }
}
