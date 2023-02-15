package org.jlab.ersap.actor.sampa.proc;

/**
 * Copyright (c) 2021, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * @author gurjyan on 8/31/22
 * @project ersap-sampa
 */
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.Arrays;

public class ELinkStats {

    private int[] syncFoundCount   = new int[28];
    private int[] syncLostCount    = new int[28];
    private int[] syncCount        = new int[28];
    private int[] heartBeatCount   = new int[28];
    private int[] dataHeaderCount  = new int[28];
    private int[] dataChannelCount = new int[160];

    public int[] getSyncFoundCount()   {return syncFoundCount;}
    public int[] getSyncLostCount()    {return syncLostCount;}
    public int[] getSyncCount()        {return syncCount;}
    public int[] getHeartBeatCount()   {return heartBeatCount;}
    public int[] getDataHeaderCount()  {return dataHeaderCount;}
    public int[] getDataChannelCount() {return dataChannelCount;}

    public void init(){
        Arrays.fill(syncFoundCount,   0);
        Arrays.fill(syncLostCount,    0);
        Arrays.fill(syncCount,        0);
        Arrays.fill(heartBeatCount,   0);
        Arrays.fill(dataHeaderCount,  0);
        Arrays.fill(dataChannelCount, 0);
    }

    public void write(OutputStream out) {

        boolean autoFlush = true;
        PrintWriter writer = new PrintWriter(out, autoFlush, Charset.forName("US_ASCII"));

        writer.println();

        for (int ii = 0; ii < 28; ii++) {
            writer.print("-------------------------------- elink = ");
            writer.print(ii);
            writer.print(" ----------------------------------------\n");

            writer.print(" sync count = ");
            writer.print(getSyncCount()[ii]);
            writer.print("  sync found count = ");
            writer.print(getSyncFoundCount()[ii]);
            writer.print("  sync lost count = ");
            writer.println(getSyncLostCount()[ii]);
            writer.println();

            writer.print(" data header count = ");
            writer.print(getDataHeaderCount()[ii]);
            writer.print("  heartbeat count = ");
            writer.println(getHeartBeatCount()[ii]);
            writer.println();
        }

        writer.println("\n --------------------------------------------- channel counts -----------------------------------------------");

        for (int chip = 0; chip < 5; chip++) {
            for (int ch = 0; ch < 32; ch++) {
                int channel = chip * 32 + ch;

                if ((channel % 16) == 0) {
                    writer.println();
                    writer.print("chan ");
                    writer.print(channel);
                    writer.print(": ");
                }

                if ((channel % 16) == 8) {
                    writer.print("  ");
                }

                writer.print(getDataChannelCount()[channel]);
                writer.print(" ");

                if (channel == 79) {
                    writer.println();
                }
            }
        }
        writer.println("\n------------------------------------------------------------------------------------------------------------\n\n");
    }
}
