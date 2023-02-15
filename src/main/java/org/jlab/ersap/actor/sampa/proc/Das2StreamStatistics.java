package org.jlab.ersap.actor.sampa.proc;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

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
public class Das2StreamStatistics {
    private double[] mean;
    private double[] sdv;

    public Das2StreamStatistics() {
        mean = new double[160];
        sdv = new double[160];
    }

    public void calculateStats(ByteBuffer[] data) {

        double m, M2, variance, delta, dataPt;

        // reset stat arrays
        reset();

        // How much data do we have?
        int sampleLimit = data[0].limit()/2;

        for (int channel = 0; channel < 160; channel++) {
            m = 0;
            M2 = 0;
            variance = 0;

            for (int sample = 0; sample < sampleLimit; sample++) {
                dataPt = data[channel].getShort(2*sample); // ADC sample
                delta = dataPt - m;
                m  += delta / (sample + 1);
                M2 += delta * (dataPt - m);
                variance = M2 / (sample + 1);
            };
            System.out.println("\n");

            mean[channel] = m;
            sdv[channel]  = Math.sqrt(variance);
        }
    }

    public void printStats(OutputStream out, boolean json) {

        boolean autoFlush = true;
        PrintWriter writer = new PrintWriter(out, autoFlush, StandardCharsets.US_ASCII);

        writer.print(5);
        writer.write((json ? "{\n" : ""));
        writer.write((json ? "\"input_name\" : " : "Input name : "));
        writer.write("\"");
        writer.write("streaming");
        writer.write("\"");
        writer.write((json ? ",\n" : "\n"));

        if (json) {
            writer.write("\"mean\": [");
            for (int channel = 0; channel < 160; channel++) {
                writer.printf("%8.4f", mean[channel]);
                writer.write((channel == 159 ? "]" : ", "));
            }

            writer.write(",\n");
            writer.write("\"stdev\": [");

            for (int channel = 0; channel < 160; channel++) {
                writer.printf("%6.4f", sdv[channel]);
                writer.write((channel == 159 ? "]" : ", "));
            }
        }
        else {
            for (int channel = 0; channel < 160; channel++) {
                writer.write("[ CHA ");
                writer.printf("%2d] : ", channel);
                writer.printf("%8.4f   ", mean[channel]);
                writer.printf("%6.4f\n", sdv[channel]);

            };
        };
        writer.write((json ? "\n}" : ""));
        writer.write("\n");
    }

private void reset(){
    Arrays.fill(mean, 0);
    Arrays.fill(sdv, 0);
}
    public double[] getMean() {
        return mean;
    }

    public double[] getSdv() {
        return sdv;
    }
}
