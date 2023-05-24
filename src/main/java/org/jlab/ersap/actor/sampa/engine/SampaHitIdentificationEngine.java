package org.jlab.ersap.actor.sampa.engine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import org.jlab.epsci.ersap.base.ErsapUtil;
import org.jlab.epsci.ersap.base.error.ErsapException;
import org.jlab.epsci.ersap.engine.Engine;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.ersap.actor.datatypes.DasDataType;
import org.jlab.ersap.actor.sampa.proc.DasHistogram;
import org.jlab.ersap.actor.sampa.proc.SampaDasGson;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * Copyright (c) 2021, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * @author gurjyan on 5/23/23
 * @project ersap-actor
 */
public class SampaHitIdentificationEngine implements Engine {
    private static String THR_ESTIMATE = "threshold";
    private int thr_estimate;
    private static String FEC_COUNT = "fec_count";
    private int fecCount;
    private static String FRAME_TITLE = "frame_title";
    private String frameTitle;
    private static String FRAME_WIDTH = "frame_width";
    private int frameWidth;
    private static String FRAME_HEIGHT = "frame_height";
    private int frameHeight;
    private static String HIST_TITLES = "hist_titles";
    private ArrayList<String> histTitles;
    private static String HIST_BINS = "hist_bins";
    private int histBins;
    private static String HIST_MIN = "hist_min";
    private double histMin;
    private static String HIST_MAX = "hist_max";
    private double histMax;
    private static String GRID_SIZE = "grid_size";
    private int gridSize;

    private DasHistogram histogram;

    private int chNum;

    @Override
    public EngineData configure(EngineData input) {
        if (input.getMimeType().equalsIgnoreCase(EngineDataType.JSON.mimeType())) {
            String source = (String) input.getData();
            JSONObject opts = new JSONObject(source);
            if (opts.has(FEC_COUNT)) {
                fecCount = opts.getInt(FEC_COUNT);
                if (fecCount == 0) {
                    chNum = 80;
                } else {
                    // Each FEC has 2 GBT stream, each having 80 channel data
                    chNum = 80 * fecCount * 2;
                }
            }
            if (opts.has(THR_ESTIMATE)) {
                thr_estimate = opts.getInt(THR_ESTIMATE);
            }
            if (opts.has(FRAME_TITLE)) {
                frameTitle = opts.getString(FRAME_TITLE);
            }
            if (opts.has(FRAME_WIDTH)) {
                frameWidth = opts.getInt(FRAME_WIDTH);
            }
            if (opts.has(FRAME_HEIGHT)) {
                frameHeight = opts.getInt(FRAME_HEIGHT);
            }
            if (opts.has(GRID_SIZE)) {
                gridSize = opts.getInt(GRID_SIZE);
            }
            if (opts.has(HIST_TITLES)) {
                histTitles = new ArrayList<>();
                String ht = opts.getString(HIST_TITLES);
                StringTokenizer st = new StringTokenizer(ht, ",");
                while (st.hasMoreTokens()) {
                    histTitles.add(st.nextToken().trim());
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
            histogram = new DasHistogram(frameTitle, histTitles,
                    gridSize, frameWidth, frameHeight,
                    histBins, histMin, histMax);
        }
        return null;
    }

    @Override
    public EngineData execute(EngineData input) {
        double m = 0, M2 = 0, variance = 0, sdv, delta, dataPt;
        ByteBuffer bb = (ByteBuffer) input.getData();
        ByteBuffer[] data;
        try {
            data = DasDataType.deserialize(bb);
            int sampleLimit = data[0].limit() / 2;
            for (int channel = 0; channel < chNum; channel++) {
                String title = String.valueOf(channel);
                if (histTitles.contains(title)) {
                    double[] _sData = new double[sampleLimit];
                    for (int sample = 0; sample < sampleLimit; sample++) {
                        try {
                            dataPt = data[channel].getShort(2 * sample);
                            if (dataPt > thr_estimate) {
                                // This is a hit, read all hit samples and add it to the histogram.
//                                sdv = Math.sqrt(variance);
                                int hitSample = sample;
                                System.out.println("Start of a hit on the channel = " + channel + " at the sample = " + hitSample);
                                do {
                                    hitSample++;
                                    dataPt = data[channel].getShort(2 * hitSample);
                                    // Add the hit sample to the histogram array.
                                    // Subtract pedestal mean value.
                                    _sData[hitSample] = dataPt - m;
                                } while (dataPt <= thr_estimate);
                                System.out.println("End of a hit on the channel = " + channel + " at the sample = " + hitSample);
                                sample = hitSample;
                            } else {
                                // This is estimated threshold, calculate mean and sdv.
                                delta = dataPt - m;
                                m += delta / (sample + 1);
                                M2 += delta * (dataPt - m);
                                variance = M2 / (sample + 1);
                            }
                        } catch (IndexOutOfBoundsException e) {
                            e.printStackTrace();
                        }
                    }
                    histogram.update(title, _sData);
                }
            }

        } catch (ErsapException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public EngineData executeGroup(Set<EngineData> set) {
        return null;
    }

    @Override
    public Set<EngineDataType> getInputDataTypes() {
        return ErsapUtil.buildDataTypes(EngineDataType.BYTES,
                EngineDataType.JSON);
    }

    @Override
    public Set<EngineDataType> getOutputDataTypes() {
        return ErsapUtil.buildDataTypes(EngineDataType.BYTES,
                EngineDataType.JSON);
    }

    @Override
    public Set<String> getStates() {
        return null;
    }

    @Override
    public String getDescription() {
        return "Hit identification emgine";
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

    public static void main(String[] args) throws IOException, JSONException {
        String jsonFileName = args[0];
        System.out.printf(jsonFileName);
        Gson gson = new Gson();

        JsonReader jsonReader = new JsonReader(new FileReader(jsonFileName));

        jsonReader.beginArray();

        while (jsonReader.hasNext()) {
            System.out.printf("DDD");
            Map<Integer, double[]> samples = gson.fromJson(jsonReader, Map.class);
            for (Integer s : samples.keySet()) {
                System.out.println(s);
            }
        }
        jsonReader.endArray();
        jsonReader.close();

    }
}
