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
import java.util.*;

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
    private int frameWidth = 1200;
    private static String FRAME_HEIGHT = "frame_height";
    private int frameHeight = 1200;
    private static String HIST_TITLES = "hist_titles";
    private ArrayList<String> histTitles = new ArrayList<>(Arrays.asList("1-2-0", "1-2-1", "1-2-2", "1-2-1&2"));;
    private static String COINCIDENCE = "coincidence";
    private ArrayList<String> concidence;
    private static String HIST_BINS = "hist_bins";
    private int histBins = 100;
    private static String HIST_MIN = "hist_min";
    private double histMin = 0;
    private static String HIST_MAX = "hist_max";
    private double histMax = 8000;
    private static String GRID_SIZE = "grid_size";
    private int gridSize = 2;
    private static String SCATTER_RESET = "scatter_reset";
    private static String SCATTER_YMIN = "scatter_y_min";
    private static String SCATTER_YMAX = "scatter_y_max";
    private boolean scatterReset = true;
    private double scatter_y_min = 0 , scatter_y_max = 2000;

    private LiveHistogram liveHist;

    private static String DELTA_T = "delta_t";
    private int deltaT = 20;

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
        if (opts.has(COINCIDENCE)) {
            concidence = new ArrayList<>();
            String ht = opts.getString(COINCIDENCE);
            StringTokenizer st = new StringTokenizer(ht, ",");
            while (st.hasMoreTokens()) {
                concidence.add(st.nextToken().trim());
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
        if (opts.has(SCATTER_YMIN)) {
            scatter_y_min = opts.getDouble(SCATTER_YMIN);
        }
        if (opts.has(SCATTER_YMAX)) {
            scatter_y_max = opts.getDouble(SCATTER_YMAX);
        }


        if (opts.has(HIST_MIN)) {
            histMin = opts.getDouble(HIST_MIN);
        }
        liveHist = new LiveHistogram(frameTitle, histTitles, concidence, gridSize,
                frameWidth, frameHeight, histBins, histMin, histMax, scatter_y_min, scatter_y_max);

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
        Set<FADCHit> conis = new HashSet<>();
        Set<String> conisNames = new HashSet<>();
        List<RocTimeSliceBanks> banks = (List<RocTimeSliceBanks>)event;
        if (!banks.isEmpty()) {
            if (scatterReset) liveHist.resetScatter();
            for (RocTimeSliceBanks bank : banks) {
                List<FADCHit> hits = bank.getHits();
                System.out.println();
                System.out.println("DDD ------------ Frame = "+bank.getFrameNumber());

                for (FADCHit hit : hits) {
                    System.out.println(hit);
                    liveHist.update(hit.getName(),hit);
                    liveHist.updateScatter(hit.withTime(hit.time()-bank.getTimeStamp()));
                    if(concidence.contains(hit.getName())){
                        conis.add(hit);
                        conisNames.add(hit.getName());
                    }
                }
                // Coincidence
                 if(conisNames.containsAll(concidence)){
                     int totlaCharge = 0;
                     long time = 0;
                     StringBuilder title = new StringBuilder();
                   for(FADCHit h: conis) {
                       totlaCharge += h.charge();
                       time = h.time() - bank.getTimeStamp();
                       title.append(h.getName()+"&");
                   }
                   String t = String.valueOf(title);
                     liveHist.update(t.substring(0, t.length() - 1),new FADCHit(0,0,0,+totlaCharge, time));
                }
                System.out.println("DDD ------------ Time  = "+bank.getTimeStamp());
            }
        }
    }

    @Override
    protected EngineDataType getDataType() {
        return JavaObjectType.JOBJ;
    }
}
