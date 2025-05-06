package org.jlab.ersap.actor.coda.proc;

import org.jlab.ersap.actor.coda.proc.fadc.FADCHit;
import twig.data.H1F;
import twig.data.H2F;
import twig.data.TDirectory;
import twig.graphics.TGDataCanvas;
import twig.math.DataFitter;
import twig.math.F1D;
import twig.widgets.PaveText;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Copyright (c) 2021, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * @author gurjyan on 4/27/22
 * @project ersap-coda
 */
public class LiveHistogram {

    private Map<String, H1F> histograms = new HashMap<>();
//    private Map<String, H1F> histograms2 = new HashMap<>();
    private H1F coincidenceHist;
    private H2F scatter;
    private TGDataCanvas cc;
    private TGDataCanvas ccc;
    private TDirectory histDir;
    private static String ERSAP_USER_DATA;

    public LiveHistogram(String frameTitle, ArrayList<String> histTitles,
                         ArrayList<String> coincidenceTitle,
                         int gridSize, int frameWidth, int frameHeight,
                         int histBins, double histMin, double histMax,
                         double scatterYMin, double scatterYMax) {

        JFrame frame = new JFrame(frameTitle);
        frame.setSize(frameWidth, frameHeight);
        JPanel panel = new JPanel();
        GridLayout gl = new GridLayout(gridSize, gridSize);
        gl.setHgap(10);
        gl.setVgap(10);
        panel.setLayout(gl);
        frame.getContentPane().add(panel);

        // create canvases with associated histograms,
        // and add them to the panel
        for (String s : histTitles) {
            TGDataCanvas c = new TGDataCanvas();
            c.setAxisFont(new Font("Avenir", Font.PLAIN, 6));
            panel.add(c);
            c.initTimer(600);
            H1F hist = new H1F(s, histBins, histMin, histMax);
            hist.setTitleX(s);
            histograms.put(s, hist);
            c.region().draw(hist);
        }
        frame.setVisible(true);

//        if (histTitles2 != null) {
//            JFrame frame2 = new JFrame(frameTitle);
//            frame2.setSize(frameWidth, frameHeight);
//            JPanel panel2 = new JPanel();
//            GridLayout gl2 = new GridLayout(gridSize, gridSize);
//            gl2.setHgap(10);
//            gl2.setVgap(10);
//            panel2.setLayout(gl2);
//            frame2.getContentPane().add(panel2);
//
//            // create canvases with associated histograms,
//            // and add them to the panel
//            for (String s : histTitles2) {
//                TGDataCanvas c = new TGDataCanvas();
//                c.setAxisFont(new Font("Avenir", Font.PLAIN, 6));
//                panel2.add(c);
//                c.initTimer(600);
//                H1F hist = new H1F(s, histBins, histMin, histMax);
//                hist.setTitleX(s);
//                histograms2.put(s, hist);
//                c.region().draw(hist);
//            }
//            frame2.setVisible(true);
//        }

        JFrame frame3 = new JFrame("ERSAP: channel vs hitTime");
        cc = new TGDataCanvas();
        frame3.add(cc);
        frame3.setSize(600, 600);
        cc.initTimer(600);
        scatter = new H2F("cvh", 100, 0, 70000, 100, scatterYMin, scatterYMax);
        cc.region().draw(scatter);
        frame3.setVisible(true);

        JFrame frame4 = new JFrame("ERSAP: Integral");
        ccc = new TGDataCanvas();
        frame4.add(ccc);
        frame4.setSize(600, 600);
        ccc.initTimer(600);
        StringBuffer coinTitle = new StringBuffer();
        for(String t:coincidenceTitle){
            coinTitle.append(t+"&");
        }
        String tt = String.valueOf(coinTitle);
        String title = tt.substring(0, tt.length() - 1);
        coincidenceHist = new H1F(title, 100, 0, 12000);
        coincidenceHist.setTitleX(title);
        histograms.put(title,coincidenceHist);
        ccc.region().draw(coincidenceHist);
        frame4.setVisible(true);

        // create directory
        histDir = new TDirectory();
        ERSAP_USER_DATA = System.getenv("ERSAP_USER_DATA");

    }

    public void update(String name, FADCHit v) {
        if (histograms.containsKey(name)) {
            histograms.get(name).fill(v.charge());

//        } else if (histograms2.containsKey(name)) {
//            histograms2.get(name).fill(v.charge());
//            if (v.slot() == 15) {
//                scatter.fill(v.time(), v.channel() + 16);
//            } else {
//                scatter.fill(v.time(), v.channel());
//            }
        }
    }
    public void updateScatter(FADCHit v){
        scatter.fill(v.time(), v.getId());
    }

    public void resetScatter() {
        scatter.reset();
    }

    public void writeHist() {
        for(String s: coincidenceHist.getStatText()){
            System.out.println(s);
        }
        fit();
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        for (H1F h1 : histograms.values()) {
            histDir.add(ERSAP_USER_DATA + "/data/output", h1);
        }
//        for (H1F h2 : histograms2.values()) {
//            histDir.add(ERSAP_USER_DATA + "/data/output", h2);
//        }
        histDir.add(ERSAP_USER_DATA + "/data/output", scatter);
        histDir.write(ERSAP_USER_DATA + "/data/output/hist_desy.twig");
    }

    public void fit() {
//       F1D func = new F1D("func","[a]+[b]*x+[c]*x*x+[d]*gaus(x,[e],[f])",4000.0,8000.0);
//       func.setParameters(new double[]{1.0,1.0,1.0,150,0.6,0.02});
//       func.setParLimits(3,0,1500);
//       func.setParLimits(4,0.2,0.8);
//       func.setParLimits(5,0.0,0.05);
//       func.attr().setLineWidth(2);
//       func.attr().setLineStyle(2);
//       func.fit(sumHist,"N");
//
//       PaveText    paveStats = new PaveText(func.getStats("M"),0.05,0.95, false,8);
//       paveStats.setNDF(true).setMultiLine(true);
//       ccc.region(0).draw(sumHist).draw(func,"same").draw(paveStats);

        //a - height, b - pick position, c- standard deviation
        F1D func = new F1D("func","[a]*gaus(x,[b],[c])",3000,11000);
        func.setParameters(new double[]{2000,9000,1000});
        func.setParLimits(0,0,2000);
        func.setParLimits(1,3000,11000);
        func.setParLimits(2,0.0,1000);

        func.attr().setLineWidth(1);
        DataFitter.fit(func, coincidenceHist,"N");

        PaveText paveStats = new PaveText(func.getStats("M").toString(),0.05,0.95, false,8);
        paveStats.setNDF(true);

        ccc.region(0).draw(coincidenceHist).draw(func,"same").draw(paveStats);
        ccc.repaint();

    }

    public void readPlotHist() {

//        TDirectory dir2 = new TDirectory("hist_desy.twig");

//        dir2.show(); // will print content of the file

//        H1F h2 = (H1F) dir2.get(ERSAP_USER_DATA + "/data/output");
//        TGCanvas c = new TGCanvas();
//        c.view().region().draw(h2);
//        c.repaint();
    }
}

