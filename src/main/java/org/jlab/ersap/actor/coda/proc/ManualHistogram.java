package org.jlab.ersap.actor.coda.proc;

import twig.data.H2F;
import twig.graphics.TGDataCanvas;

import javax.swing.*;

/**
 * Copyright (c) 2021, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * @author gurjyan on 5/5/22
 * @project ersap-coda
 */
public class ManualHistogram {
    private H2F scatter;
    private TGDataCanvas cc;
    public ManualHistogram() {
        JFrame frame = new JFrame("ERSAP: channel vs hitTime");
        cc = new TGDataCanvas();

        frame.add(cc);
        frame.setSize(600, 600);

//        cc.initTimer(600);
        scatter = new H2F("cvh", 100, 0, 70000, 100, 0, 33);
        cc.region().draw(scatter);
        frame.setVisible(true);

    }

    public void update(String name, FADCHit v) {
        if (v.slot() == 19) {
            scatter.fill(v.time(), v.channel() + 16);
        } else {
            scatter.fill(v.time(), v.channel());
        }
    }
    public void repaint() {
        cc.repaint();
    }

    public void reset() {
        scatter.reset();
    }

}


