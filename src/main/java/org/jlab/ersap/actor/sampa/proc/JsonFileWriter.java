package org.jlab.ersap.actor.sampa.proc;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Copyright (c) 2021, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * @author gurjyan on 3/17/23
 * @project ersap-coda
 * @deprecated
 */
public class JsonFileWriter {

    public static void printFrame(OutputStream out, Map<Integer, double[]> frame) {
        boolean autoFlush = true;
        PrintWriter writer = new PrintWriter(out, autoFlush, StandardCharsets.US_ASCII);

        for (Integer channel : frame.keySet()) {
            writer.print(5);
            writer.write("{\n");
            writer.write("\"channel\" : " + channel);
            writer.write("\"");
            writer.write("streaming");
            writer.write("\"");
            writer.write(",\n");

            writer.write("\"samples\": [");
            double[] dp = frame.get(channel);
            for (int i = 0; i < dp.length; i++) {
                writer.printf("%8.4f", dp[i]);
                writer.write((i == dp.length ? "]" : ", "));
            }
            writer.write(",\n");
        }
        writer.write("\n}");
        writer.write("\n");
    }
}
