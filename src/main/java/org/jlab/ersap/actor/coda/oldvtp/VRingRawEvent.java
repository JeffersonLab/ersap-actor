package org.jlab.ersap.actor.coda.oldvtp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Copyright (c) 2021, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * @author gurjyan on 2/23/23
 * @project ersap-coda
 */
public class VRingRawEvent {
    private int streamId;
    //    private BigInteger recordNumber;
    private long recordNumber;
    private byte[] payload;
    private ByteBuffer payloadBuffer;
    private int payloadDataLength;
    private int partLength1;
    private int partLength2;

    public VRingRawEvent() {
        payload = new byte[100000];
        payloadBuffer = ByteBuffer.wrap(payload);
        payloadBuffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    public int getStreamId() {
        return streamId;
    }

    public void setStreamId(int streamId) {
        this.streamId = streamId;
    }

//    public BigInteger getRecordNumber() {
//        return recordNumber;
//    }
//
//    public void setRecordNumber(BigInteger recordNumber) {
//        this.recordNumber = recordNumber;
//    }

    public long getRecordNumber() {
        return recordNumber;
    }

    public void setRecordNumber(long recordNumber) {
        this.recordNumber = recordNumber;
    }

    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    public int getPayloadDataLength() {
        return payloadDataLength;
    }

    public void setPayloadDataLength(int payloadDataLength) {
        this.payloadDataLength = payloadDataLength;
    }

    public int getPartLength1() {
        return partLength1;
    }

    public void setPartLength1(int partLength1) {
        this.partLength1 = partLength1;
    }

    public int getPartLength2() {
        return partLength2;
    }

    public void setPartLength2(int partLength2) {
        this.partLength2 = partLength2;
    }

    public ByteBuffer getPayloadBuffer(){
        return payloadBuffer;
    }
}

