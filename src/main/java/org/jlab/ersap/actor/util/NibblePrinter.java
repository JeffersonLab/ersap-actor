package org.jlab.ersap.actor.util;

public class NibblePrinter {
    public static void printHexBlocks(byte[] data, int targetCount) {
        int matchCount = 0;

        for (int i = 0; i + 3 < data.length; i += 4) {
            int word = ((data[i] & 0xFF) << 24) | ((data[i + 1] & 0xFF) << 16)
                    | ((data[i + 2] & 0xFF) << 8) | (data[i + 3] & 0xFF);

            // Print 8 hex digits (32 bits) in uppercase
            System.out.printf("%08X\n", word);

            if (word == 0xC0DA0100) {
                matchCount++;
                System.out.println("--- CODA MAGIC WORD ---\n");
            }

            if (matchCount >= targetCount) break;
        }
    }

}
