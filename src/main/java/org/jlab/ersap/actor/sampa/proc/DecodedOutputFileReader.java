package org.jlab.ersap.actor.sampa.proc;

import java.io.*;

/**
 * Copyright (c) 2021, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * @author gurjyan on 2/24/23
 * @project ersap-coda
 */
public class DecodedOutputFileReader {
    public static void main(String[] args) {

        if (args.length < 1) {
            System.out.println("Please provide input file name");
            System.exit(0);
        }
        String inputFileName = args[0];
        try {
            DataInputStream dataInputStream = new DataInputStream(new FileInputStream(inputFileName));
            while(true) {
                for (int j = 0; j <= 6; j++) {
                    System.out.println("Link = " + j);
                    for (int i = 0; i < 80; i++) {
                        System.out.println("channel = " + i + " value = " + dataInputStream.readShort());
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

