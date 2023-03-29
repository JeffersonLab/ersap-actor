package org.jlab.ersap.actor.sampa.proc;

import twig.data.H1F;
import twig.graphics.TGDataCanvas;

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
 * @author gurjyan on 9/29/22
 * @project ersap-sampa
 */
public class DasHistogram {
    private Map<String, H1F> histograms = new HashMap<>();

    public DasHistogram(String frameTitle, ArrayList<String> histTitles,
                        int gridSize, int frameWidth, int frameHeight,
                        int histBins, double histMin, double histMax) {

        // Create frame
        JFrame frame = new JFrame("ERSAP");
        frame.setSize(600, 600);

        // Create panel with the grid layout
        JPanel panel = new JPanel();
        GridLayout gl = new GridLayout(gridSize, gridSize);
        gl.setHgap(10);
        gl.setVgap(10);
        panel.setLayout(gl);
        frame.getContentPane().add(panel);

        // Create number of canvas/histogram and add them to the panel
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
    }

    public void update(String name, short[] data) {
        if (histograms.containsKey(name)) {
            for (short s : data) {
                histograms.get(name).fill(s);
            }
        }
    }
}
