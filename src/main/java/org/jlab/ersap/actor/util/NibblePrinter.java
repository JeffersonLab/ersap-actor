package org.jlab.ersap.actor.util;

public class NibblePrinter {
    public static void printNibbles(byte[] data) {
        int nibbleCount = 0;

        for (byte b : data) {
            int highNibble = (b >> 4) & 0x0F;
            int lowNibble = b & 0x0F;

            System.out.print(Integer.toHexString(highNibble));
            nibbleCount++;
            if (nibbleCount % 8 == 0) System.out.println();

            System.out.print(Integer.toHexString(lowNibble));
            nibbleCount++;
            if (nibbleCount % 8 == 0) System.out.println();
        }

        // If the last line wasn't terminated, add a newline.
        if (nibbleCount % 8 != 0) System.out.println();
    }
}
