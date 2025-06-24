package org.jlab.ersap.actor.coda.proc;

import org.jlab.ersap.actor.coda.proc.fadc.FADCHit;
import org.jlab.ersap.actor.coda.proc.fadc.RocTimeFrameBank;
import org.jlab.ersap.actor.util.IASource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class EVIO4EvtParser implements IASource, Runnable {
    private boolean debug;
    private static final int WORD_SIZE = 4;
    private static final int MAGIC_WORD = 0xC0DA0100;
    // Queue
    private final BlockingQueue<List<RocTimeFrameBank>> queue;
    private AtomicBoolean running = new AtomicBoolean(true);
    private ByteBuffer dataBuffer;

    public EVIO4EvtParser(String fileName, int queueCapacity, boolean debug) {
        this.queue = new LinkedBlockingQueue<>(queueCapacity);
        this.debug = debug;
        try {
           byte[] fileBytes = Files.readAllBytes(Paths.get(fileName));
            dataBuffer = ByteBuffer.wrap(fileBytes).order(ByteOrder.BIG_ENDIAN);
            Thread thread = new Thread(this);
            thread.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void startParsing(ByteBuffer buffer) throws IOException, InterruptedException {
       List<RocTimeFrameBank> timeFrameBanks = new ArrayList<>();

        while (buffer.remaining() >= WORD_SIZE) {
            buffer.mark();
            int word = buffer.getInt();

            if (word == MAGIC_WORD) {
                if (debug) System.out.println("DDD Found block header.");
                if (buffer.remaining() < WORD_SIZE * 6) continue;

//                    buffer.position(buffer.position() + WORD_SIZE * 5);

                // test to see if this is evio v4.0
                buffer.position(buffer.position() + WORD_SIZE * 3);
                int word2 = (buffer.getInt() >>> 16) & 0xFFFF;
                if (debug) System.out.println("DDD========> v4.0 identifier = " + Integer.toHexString(word2));
                buffer.getInt();
                // end of the test

                int frameNumber = buffer.getInt();

                if (buffer.remaining() < WORD_SIZE * 2) continue;
                long timeStamp = Integer.toUnsignedLong(buffer.getInt()) | ((long) buffer.getInt() << 32);

                if (debug) System.out.println("DDD frameNumber = " + frameNumber + " timeStamp = " + timeStamp);



                if (buffer.remaining() < WORD_SIZE) continue;
                int nextWord = buffer.getInt();
                int upperBytes = (nextWord >>> 16) & 0xFFFF;

                if (upperBytes != 0x4185) continue;
                int eLength = nextWord & 0xFFFF;
                if (debug) System.out.println("DDD  => 0x" + Integer.toHexString(upperBytes) +
                        " 0x" + Integer.toHexString(eLength));

                int payloads = 0;
                for (int i = 0; i < eLength; i++) {
                    int pp = buffer.getInt();
                    int p1 = (pp >>> 16) & 0xFFFF;
                    int p2 = pp & 0xFFFF;
                    int p1ModuleID = (p1 >>> 8) & 0xF;
                    if (p1ModuleID != 0) payloads++;
                    int p1PayloadID = p1 & 0x1F;
                    int p2ModuleID = (p2 >>> 8) & 0xF;
                    if (p2ModuleID != 0) payloads++;
                    int p2PayloadID = p2 & 0x1F;
                    if (debug) System.out.println("DDD===> payloadModule_" + i + "1 " + p1ModuleID +
                            " payloadID_" + i + "1 " + p1PayloadID +
                            " payloadModule_" + i + "2 " + p2ModuleID +
                            " payloadID_" + i + "2 " + p2PayloadID);
                    if (debug) System.out.println();
                }

                for (int i = 0; i < payloads; i++) {

                    if (buffer.remaining() < WORD_SIZE * 2) continue;
                    RocTimeFrameBank rocTimeFrameBank = new RocTimeFrameBank();
                    rocTimeFrameBank.setFrameNumber(frameNumber);
                    rocTimeFrameBank.setTimeStamp(timeStamp);

                    int payloadPortLength = buffer.getInt();
                    int payloadHeader = buffer.getInt();
                    int payloadID = (payloadHeader >>> 16) & 0xFFFF;

                    if (debug) System.out.println("DDD PayloadID = " + payloadID);

                    int remainingPayloadWords = payloadPortLength - 1;
                    if (buffer.remaining() < remainingPayloadWords * WORD_SIZE) continue;

                    byte[] payloadBytes = new byte[remainingPayloadWords * WORD_SIZE];
                    buffer.get(payloadBytes);

                    // Adding hits in this payload board
                    rocTimeFrameBank.addHits(parseFADCPayload(timeStamp, payloadID, payloadBytes));
//                    if(debug) {
                        for (FADCHit h : rocTimeFrameBank.getHits()) {
                            System.out.println(h);
                        }
//                    }
                    timeFrameBanks.add(rocTimeFrameBank);
                }
                enqueue(timeFrameBanks);
            } else {
                buffer.reset();
                buffer.position(buffer.position() + WORD_SIZE);
            }
        }
    }


    private List<FADCHit> parseFADCPayload(Long frame_time_ns, int payloadId, byte[] ba) {
        List<FADCHit> hits = new ArrayList<>();
        IntBuffer intBuf = ByteBuffer.wrap(ba).order(ByteOrder.BIG_ENDIAN).asIntBuffer();
        int[] pData = new int[intBuf.remaining()];
        intBuf.get(pData);

        for (int word : pData) {
            int wordType = (word >>> 30) & 0x3;

            if (wordType == 0b00) {
                int q = word & 0x1FFF;                   // bits 12:0
                int channel = (word >>> 13) & 0xF;       // bits 16:13
                long t_offset = ((word >>> 17) & 0x1FFF) * 4L; // bits 29:17, 4ns units
                long hit_time = frame_time_ns + t_offset;

                // Filter out invalid or junk values:
                boolean isPowerOf2 = (q != 0) && ((q & (q - 1)) == 0);
                boolean looksValid =
                        q > 0 &&
                                !isPowerOf2 && // reject pure powers of 2
                                channel >= 0 && channel < 16 &&
                                (hit_time > frame_time_ns) &&
                                (hit_time - frame_time_ns) < 100_000_000; // max 100ms drift

                if (!looksValid) continue;

                FADCHit hit = new FADCHit(1, payloadId, channel, q, hit_time);
                hits.add(hit);

                if (debug)
                    System.out.printf("HIT: 0x%08X → ch=%d q=%d t=%d\n", word, channel, q, hit_time);
            } else {
                if (debug)
                    System.out.printf("SKIP: Non-hit word: 0x%08X (type=%d)\n", word, wordType);
            }
        }

        if (debug)
            for (FADCHit hit : hits)
                System.out.println(hit);
        return hits;
    }

    private void enqueue(List<RocTimeFrameBank> bank) throws InterruptedException {
        queue.put(bank); // Blocks if the queue is full
    }

    private List<RocTimeFrameBank> dequeue() throws InterruptedException {
        List<RocTimeFrameBank> banks = new ArrayList<>(queue.take());
        return banks; // Blocks if the queue is empty
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                startParsing(dataBuffer);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public Object nextEvent() {
        try {
            return dequeue();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getEventCount() {
        return Integer.MAX_VALUE;
    }

    @Override
    public ByteOrder getByteOrder() {
        return ByteOrder.BIG_ENDIAN;
    }

    @Override
    public void close() {
        running.set(false);
    }
}
