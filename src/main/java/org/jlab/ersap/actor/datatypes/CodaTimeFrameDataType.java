package org.jlab.ersap.actor.datatypes;

import org.jlab.coda.xmsg.data.xMsgD.xMsgData;
import org.jlab.coda.xmsg.data.xMsgD.xMsgPayload;
import org.jlab.epsci.ersap.base.error.ErsapException;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.engine.ErsapSerializer;
import org.jlab.ersap.actor.coda.proc.EtEvent;
import org.jlab.ersap.actor.coda.proc.FADCHit;
import org.jlab.ersap.actor.coda.proc.RocTimeFrameBank;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Copyright (c) 2025, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * @author gurjyan on 12/7/25
 * @project ersap-actor
 *
 * ERSAP native data type for CodaTimeFrame using xMsg protocol buffers.
 * Enables cross-language communication between Java and C++ engines.
 */
public class CodaTimeFrameDataType {
    private CodaTimeFrameDataType() { }

    /**
     * MIME type identifier for CodaTimeFrame native data type
     */
    public static final String MIME_TYPE = "xmsg/coda-time-frame";

    /**
     * Custom serializer for CodaTimeFrame to xMsg native format
     */
    private static class CodaTimeFrameSerializer implements ErsapSerializer {

        @Override
        public ByteBuffer write(Object data) throws ErsapException {
            if (!(data instanceof EtEvent)) {
                throw new ErsapException("Expected EtEvent object, got: " + data.getClass().getName());
            }

            EtEvent codaTimeFrame = (EtEvent) data;
            return ByteBuffer.wrap(convertToXMsgPayload(codaTimeFrame).toByteArray());
        }

        @Override
        public Object read(ByteBuffer buffer) throws ErsapException {
            try {
                xMsgPayload payload = xMsgPayload.parseFrom(buffer.array());
                return convertFromXMsgPayload(payload);
            } catch (Exception e) {
                throw new ErsapException("Failed to deserialize CodaTimeFrame: " + e.getMessage(), e);
            }
        }

        /**
         * Converts EtEvent to xMsgPayload format for cross-language compatibility
         */
        private xMsgPayload convertToXMsgPayload(EtEvent codaTimeFrame) {
            xMsgPayload.Builder payloadBuilder = xMsgPayload.newBuilder();

            // Add metadata
            payloadBuilder.addItem(xMsgPayload.Item.newBuilder()
                    .setName("event_type")
                    .setData(xMsgData.newBuilder().setSTRING("CodaTimeFrame").build())
                    .build());

            payloadBuilder.addItem(xMsgPayload.Item.newBuilder()
                    .setName("time_frame_count")
                    .setData(xMsgData.newBuilder().setVLSINT32(codaTimeFrame.getTimeFrames().size()).build())
                    .build());

            // Serialize each time frame
            for (int tfIndex = 0; tfIndex < codaTimeFrame.getTimeFrames().size(); tfIndex++) {
                List<RocTimeFrameBank> timeFrame = codaTimeFrame.getTimeFrames().get(tfIndex);

                // Add ROC count for this time frame
                payloadBuilder.addItem(xMsgPayload.Item.newBuilder()
                        .setName("time_frame_" + tfIndex + "_roc_count")
                        .setData(xMsgData.newBuilder().setVLSINT32(timeFrame.size()).build())
                        .build());

                // Serialize each ROC bank in this time frame
                for (int rocIndex = 0; rocIndex < timeFrame.size(); rocIndex++) {
                    RocTimeFrameBank rocBank = timeFrame.get(rocIndex);
                    String rocPrefix = "time_frame_" + tfIndex + "_roc_" + rocIndex;

                    // ROC metadata
                    payloadBuilder.addItem(xMsgPayload.Item.newBuilder()
                            .setName(rocPrefix + "_id")
                            .setData(xMsgData.newBuilder().setVLSINT32(rocBank.getRocID()).build())
                            .build());

                    payloadBuilder.addItem(xMsgPayload.Item.newBuilder()
                            .setName(rocPrefix + "_frame_number")
                            .setData(xMsgData.newBuilder().setVLSINT32(rocBank.getFrameNumber()).build())
                            .build());

                    payloadBuilder.addItem(xMsgPayload.Item.newBuilder()
                            .setName(rocPrefix + "_timestamp")
                            .setData(xMsgData.newBuilder().setVLSINT64(rocBank.getTimeStamp()).build())
                            .build());

                    // Hits data
                    List<FADCHit> hits = rocBank.getHits();
                    payloadBuilder.addItem(xMsgPayload.Item.newBuilder()
                            .setName(rocPrefix + "_hit_count")
                            .setData(xMsgData.newBuilder().setVLSINT32(hits.size()).build())
                            .build());

                    if (!hits.isEmpty()) {
                        // Pack all hit data into arrays for efficiency
                        int[] crates = new int[hits.size()];
                        int[] slots = new int[hits.size()];
                        int[] channels = new int[hits.size()];
                        int[] charges = new int[hits.size()];
                        long[] times = new long[hits.size()];

                        for (int i = 0; i < hits.size(); i++) {
                            FADCHit hit = hits.get(i);
                            crates[i] = hit.crate();
                            slots[i] = hit.slot();
                            channels[i] = hit.channel();
                            charges[i] = hit.charge();
                            times[i] = hit.time();
                        }

                        // Add hit arrays
                        payloadBuilder.addItem(xMsgPayload.Item.newBuilder()
                                .setName(rocPrefix + "_crates")
                                .setData(createIntArray(crates))
                                .build());

                        payloadBuilder.addItem(xMsgPayload.Item.newBuilder()
                                .setName(rocPrefix + "_slots")
                                .setData(createIntArray(slots))
                                .build());

                        payloadBuilder.addItem(xMsgPayload.Item.newBuilder()
                                .setName(rocPrefix + "_channels")
                                .setData(createIntArray(channels))
                                .build());

                        payloadBuilder.addItem(xMsgPayload.Item.newBuilder()
                                .setName(rocPrefix + "_charges")
                                .setData(createIntArray(charges))
                                .build());

                        payloadBuilder.addItem(xMsgPayload.Item.newBuilder()
                                .setName(rocPrefix + "_times")
                                .setData(createLongArray(times))
                                .build());
                    }
                }
            }

            return payloadBuilder.build();
        }

        /**
         * Converts xMsgPayload back to EtEvent
         */
        private EtEvent convertFromXMsgPayload(xMsgPayload payload) throws ErsapException {
            EtEvent codaTimeFrame = new EtEvent();

            // Find metadata items
            int timeFrameCount = 0;
            for (xMsgPayload.Item item : payload.getItemList()) {
                if ("time_frame_count".equals(item.getName())) {
                    timeFrameCount = item.getData().getVLSINT32();
                    break;
                }
            }

            // Reconstruct time frames
            for (int tfIndex = 0; tfIndex < timeFrameCount; tfIndex++) {
                List<RocTimeFrameBank> timeFrame = new ArrayList<>();

                // Find ROC count for this time frame
                int rocCount = 0;
                String rocCountKey = "time_frame_" + tfIndex + "_roc_count";
                for (xMsgPayload.Item item : payload.getItemList()) {
                    if (rocCountKey.equals(item.getName())) {
                        rocCount = item.getData().getVLSINT32();
                        break;
                    }
                }

                // Reconstruct each ROC bank
                for (int rocIndex = 0; rocIndex < rocCount; rocIndex++) {
                    String rocPrefix = "time_frame_" + tfIndex + "_roc_" + rocIndex;
                    RocTimeFrameBank rocBank = reconstructRocBank(payload, rocPrefix);
                    timeFrame.add(rocBank);
                }

                codaTimeFrame.addTimeFrame(timeFrame);
            }

            return codaTimeFrame;
        }

        /**
         * Reconstructs a RocTimeFrameBank from xMsgPayload data
         */
        private RocTimeFrameBank reconstructRocBank(xMsgPayload payload, String rocPrefix) throws ErsapException {
            RocTimeFrameBank rocBank = new RocTimeFrameBank();

            // Extract ROC metadata
            for (xMsgPayload.Item item : payload.getItemList()) {
                String name = item.getName();
                if ((rocPrefix + "_id").equals(name)) {
                    rocBank.setRocID(item.getData().getVLSINT32());
                } else if ((rocPrefix + "_frame_number").equals(name)) {
                    rocBank.setFrameNumber(item.getData().getVLSINT32());
                } else if ((rocPrefix + "_timestamp").equals(name)) {
                    rocBank.setTimeStamp(item.getData().getVLSINT64());
                }
            }

            // Extract hit count
            int hitCount = 0;
            for (xMsgPayload.Item item : payload.getItemList()) {
                if ((rocPrefix + "_hit_count").equals(item.getName())) {
                    hitCount = item.getData().getVLSINT32();
                    break;
                }
            }

            // Reconstruct hits if present
            if (hitCount > 0) {
                int[] crates = null, slots = null, channels = null, charges = null;
                long[] times = null;

                for (xMsgPayload.Item item : payload.getItemList()) {
                    String name = item.getName();
                    if ((rocPrefix + "_crates").equals(name)) {
                        crates = extractIntArray(item.getData());
                    } else if ((rocPrefix + "_slots").equals(name)) {
                        slots = extractIntArray(item.getData());
                    } else if ((rocPrefix + "_channels").equals(name)) {
                        channels = extractIntArray(item.getData());
                    } else if ((rocPrefix + "_charges").equals(name)) {
                        charges = extractIntArray(item.getData());
                    } else if ((rocPrefix + "_times").equals(name)) {
                        times = extractLongArray(item.getData());
                    }
                }

                // Create FADCHit objects
                if (crates != null && slots != null && channels != null && charges != null && times != null) {
                    for (int i = 0; i < hitCount; i++) {
                        FADCHit hit = new FADCHit(crates[i], slots[i], channels[i], charges[i], times[i]);
                        rocBank.addHit(hit);
                    }
                }
            }

            return rocBank;
        }

        // Helper methods for array serialization
        private xMsgData createIntArray(int[] array) {
            xMsgData.Builder builder = xMsgData.newBuilder();
            for (int value : array) {
                builder.addVLSINT32A(value);
            }
            return builder.build();
        }

        private xMsgData createLongArray(long[] array) {
            xMsgData.Builder builder = xMsgData.newBuilder();
            for (long value : array) {
                builder.addVLSINT64A(value);
            }
            return builder.build();
        }

        private int[] extractIntArray(xMsgData data) {
            int[] result = new int[data.getVLSINT32ACount()];
            for (int i = 0; i < result.length; i++) {
                result[i] = data.getVLSINT32A(i);
            }
            return result;
        }

        private long[] extractLongArray(xMsgData data) {
            long[] result = new long[data.getVLSINT64ACount()];
            for (int i = 0; i < result.length; i++) {
                result[i] = data.getVLSINT64A(i);
            }
            return result;
        }
    }

    /**
     * The ERSAP EngineDataType for CodaTimeFrame using xMsg native format
     */
    public static final EngineDataType CODA_TIME_FRAME =
            new EngineDataType(MIME_TYPE, new CodaTimeFrameSerializer());
}