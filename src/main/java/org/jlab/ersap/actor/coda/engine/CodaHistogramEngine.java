package org.jlab.ersap.actor.coda.engine;

import org.jlab.epsci.ersap.base.ErsapUtil;
import org.jlab.epsci.ersap.engine.Engine;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.ersap.actor.coda.proc.LiveHistogram;
import org.jlab.ersap.actor.coda.proc.fadc.FADCHit;
import org.jlab.ersap.actor.coda.proc.fadc.RocTimeFrameBank;
import org.jlab.ersap.actor.datatypes.JavaObjectType;
import org.json.JSONObject;

import java.util.*;

public class CodaHistogramEngine implements Engine {
    private static String FRAME_TITLE = "frame_title";
    private String frameTitle = "ERSAP";
    private static String FRAME_WIDTH = "frame_width";
    private int frameWidth = 1200;
    private static String FRAME_HEIGHT = "frame_height";
    private int frameHeight = 1200;
    private static String HIST_TITLES = "hist_titles";
    private ArrayList<String> histTitles = new ArrayList<>(Arrays.asList("1-2-0", "1-2-1", "1-2-2", "1-2-1&2"));;
    private static String COINCIDENCE = "coincidence";
    private ArrayList<String> coincidence;
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
            if (opts.has(HIST_TITLES)) {
                histTitles = new ArrayList<>();
                String ht = opts.getString(HIST_TITLES);
                StringTokenizer st = new StringTokenizer(ht, ",");
                while (st.hasMoreTokens()) {
                    histTitles.add(st.nextToken().trim());
                }
            }
            if (opts.has(COINCIDENCE)) {
                coincidence = new ArrayList<>();
                String ht = opts.getString(COINCIDENCE);
                StringTokenizer st = new StringTokenizer(ht, ",");
                while (st.hasMoreTokens()) {
                    coincidence.add(st.nextToken().trim());
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
            if (opts.has(DELTA_T)) {
                deltaT = opts.getInt(DELTA_T);
            }


            if (opts.has(HIST_MIN)) {
                histMin = opts.getDouble(HIST_MIN);
            }
            liveHist = new LiveHistogram(frameTitle, histTitles, coincidence, gridSize,
                    frameWidth, frameHeight, histBins, histMin, histMax, scatter_y_min, scatter_y_max);
        }
        return null;
    }

    @Override
    public EngineData execute(EngineData engineData) {
        List<FADCHit> conis = new ArrayList<>();
        Set<String> conisNames = new HashSet<>();

        List<RocTimeFrameBank> banks;
//        // Decoding
//        try {
//            banks = FadcUtil.parseEtEvent((ByteBuffer)engineData.getData());
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }

        banks = new ArrayList<>((List<RocTimeFrameBank>) engineData.getData());
        if (!banks.isEmpty()) {
            if (scatterReset) liveHist.resetScatter();
            for (RocTimeFrameBank bank : banks) {
                List<FADCHit> hits = bank.getHits();
//                System.out.println("DDD ====> Frame = "+bank.getFrameNumber()+" timeStamp = "+bank.getTimeStamp());

                for (FADCHit hit : hits) {
                    System.out.println(hit);
                    liveHist.update(hit.getName(),hit);
                    liveHist.updateScatter(hit.withTime(hit.time()-bank.getTimeStamp()));
                    if(coincidence.contains(hit.getName())){
                        conis.add(hit);
                        conisNames.add(hit.getName());
                    }
                }
                // Coincidence
                if(conisNames.containsAll(coincidence)){
                    int totlaCharge;
                    long time;
                    int size;
                    StringBuilder title = new StringBuilder();
                    for(String s: conisNames) {
                        title.append(s+"&");
                    }
                    String t = String.valueOf(title);
                    // find in the frame the groups of required channels that had hits within the specified delta_t
                    List<List<FADCHit>> coin = findCoincidenceWithinInterval(conis, deltaT);
                    if(!coin.isEmpty()) {
                        for (List<FADCHit> l:coin){
                            totlaCharge = 0;
                            time = 0;
                            size = l.size();
                            for(FADCHit h:l) {
                                totlaCharge += h.charge();
                                time += h.time() - bank.getTimeStamp();
                            }
                            liveHist.update(t.substring(0, t.length() - 1),new FADCHit(0,0,0,+totlaCharge, time/size));
                        }
                    }
                }
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
        return "Vardan";
    }

    @Override
    public void reset() {
    }

    @Override
    public void destroy() {
    }

    private  List<List<FADCHit>> findCoincidenceWithinInterval(List<FADCHit> events, long maxInterval) {
        List<List<FADCHit>> result = new ArrayList<>();

        // Sort events by time
        events.sort(Comparator.comparingLong(FADCHit::time));

        int n = events.size();
        for (int i = 0; i < n - 1; i++) {
            List<FADCHit> group = new ArrayList<>();
            group.add(events.get(i));
            for (int j = i + 1; j < n; j++) {
                long span = events.get(j).time() - events.get(i).time();
//                System.out.println("DDD====> span = "+span+" delta_t = "+maxInterval);
                if (span <= maxInterval) {
                    group.add(events.get(j));
                } else {
                    break; // further events will only increase the span
                }
            }

            if (group.size() == events.size()) {
                result.add(new ArrayList<>(group));
            }
        }

        return result;
    }

}
