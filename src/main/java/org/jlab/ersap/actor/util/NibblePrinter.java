package org.jlab.ersap.actor.util;

public class NibblePrinter {
    public static void printNibbles(byte[] data, int targetCount) {
        int nibbleCount = 0;
        int matchCount = 0;

        for (int i = 0; i < data.length - 3; i++) {
            int word = ((data[i] & 0xFF) << 24) | ((data[i + 1] & 0xFF) << 16)
                    | ((data[i + 2] & 0xFF) << 8) | (data[i + 3] & 0xFF);

            if (word == 0xc0da0100) {
                matchCount++;
            }

            if (matchCount == targetCount) {
                break;
            }

            // Print 4 bytes (8 nibbles)
            for (int j = 0; j < 4 && i + j < data.length; j++) {
                byte b = data[i + j];
                int highNibble = (b >> 4) & 0x0F;
                int lowNibble = b & 0x0F;

                System.out.print(Integer.toHexString(highNibble));
                nibbleCount++;
                if (nibbleCount % 8 == 0) System.out.println();

                System.out.print(Integer.toHexString(lowNibble));
                nibbleCount++;
                if (nibbleCount % 8 == 0) System.out.println();
            }

            if (word == 0xc0da0100) {
                System.out.println("\n--- CODA MAGIC WORD ---\n");
            }
        }

        if (nibbleCount % 8 != 0) System.out.println();
    }

}
