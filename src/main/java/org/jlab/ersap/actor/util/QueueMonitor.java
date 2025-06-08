package org.jlab.ersap.actor.util;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;

public class QueueMonitor {

    public static void checkQueue(BlockingQueue<ByteBuffer> queue) {
        if (queue.isEmpty()) {
            System.out.println("Queue is empty.");
            return;
        }

        int size = queue.size();
        int nullCount = 0;

        for (ByteBuffer buffer : queue) {
            if (buffer == null) {
                nullCount++;
            }
        }

        System.out.println("Queue size: " + size);
        if (nullCount > 0) {
            System.out.println("Warning: Queue contains " + nullCount + " null elements.");
        } else {
            System.out.println("Queue is filling and all elements are non-null.");
        }
    }
}