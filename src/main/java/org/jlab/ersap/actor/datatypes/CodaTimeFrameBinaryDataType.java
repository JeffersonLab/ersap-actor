package org.jlab.ersap.actor.datatypes;

import org.jlab.epsci.ersap.base.error.ErsapException;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.engine.ErsapSerializer;
import org.jlab.ersap.actor.coda.proc.EtEvent;
import org.jlab.ersap.actor.coda.proc.FADCHit;
import org.jlab.ersap.actor.coda.proc.RocTimeFrameBank;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

public class CodaTimeFrameBinaryDataType {

    public static final String MIME_TYPE = "binary/coda-time-frame";

    private static class CodaTimeFrameBinarySerializer implements ErsapSerializer {

        private static byte[] write(EtEvent event) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);

            List<List<RocTimeFrameBank>> timeFrames = event.getTimeFrames();
            out.writeInt(timeFrames.size());

            for (List<RocTimeFrameBank> frame : timeFrames) {
                out.writeInt(frame.size());
                for (RocTimeFrameBank roc : frame) {
                    out.writeInt(roc.getRocID());
                    out.writeInt(roc.getFrameNumber());
                    out.writeLong(roc.getTimeStamp());

                    List<FADCHit> hits = roc.getHits();
                    out.writeInt(hits.size());

                    for (FADCHit h : hits) out.writeInt(h.crate());
                    for (FADCHit h : hits) out.writeInt(h.slot());
                    for (FADCHit h : hits) out.writeInt(h.channel());
                    for (FADCHit h : hits) out.writeInt(h.charge());
                    for (FADCHit h : hits) out.writeLong(h.time());
                }
            }

            return baos.toByteArray();
        }

        private static EtEvent read(byte[] data) throws IOException {
            ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
            EtEvent event = new EtEvent();

            int tfCount = buf.getInt();
            for (int t = 0; t < tfCount; t++) {
                int rocCount = buf.getInt();
                List<RocTimeFrameBank> frame = new ArrayList<>();

                for (int r = 0; r < rocCount; r++) {
                    RocTimeFrameBank roc = new RocTimeFrameBank();
                    roc.setRocID(buf.getInt());
                    roc.setFrameNumber(buf.getInt());
                    roc.setTimeStamp(buf.getLong());

                    int hitCount = buf.getInt();
                    int[] crates = new int[hitCount];
                    int[] slots = new int[hitCount];
                    int[] chans = new int[hitCount];
                    int[] charges = new int[hitCount];
                    long[] times = new long[hitCount];

                    for (int i = 0; i < hitCount; i++) crates[i] = buf.getInt();
                    for (int i = 0; i < hitCount; i++) slots[i] = buf.getInt();
                    for (int i = 0; i < hitCount; i++) chans[i] = buf.getInt();
                    for (int i = 0; i < hitCount; i++) charges[i] = buf.getInt();
                    for (int i = 0; i < hitCount; i++) times[i] = buf.getLong();

                    for (int i = 0; i < hitCount; i++) {
                        roc.addHit(new FADCHit(crates[i], slots[i], chans[i], charges[i], times[i]));
                    }
                    frame.add(roc);
                }
                event.addTimeFrame(frame);
            }

            return event;
        }

        @Override
        public ByteBuffer write(Object data) throws ErsapException {
            if (!(data instanceof EtEvent)) {
                throw new ErsapException("Expected EtEvent, got " + data.getClass().getName());
            }
            try {
                byte[] binary = write((EtEvent) data);
                return ByteBuffer.wrap(binary);
            } catch (IOException e) {
                throw new ErsapException("Serialization failed", e);
            }
        }

        @Override
        public Object read(ByteBuffer buffer) throws ErsapException {
            try {
                byte[] binary = new byte[buffer.remaining()];
                buffer.get(binary);
                return read(binary);
            } catch (IOException e) {
                throw new ErsapException("Deserialization failed", e);
            }
        }
    }

    public static final EngineDataType CODA_TIME_FRAME =
            new EngineDataType(MIME_TYPE, new CodaTimeFrameBinarySerializer());
}
