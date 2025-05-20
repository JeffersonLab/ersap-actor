package org.jlab.ersap.actor.rtdp.source.pcap;

import org.pcap4j.core.*;
import org.pcap4j.packet.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;

public class PcapPayloadExtractor {

    public static void main(String[] args) throws PcapNativeException, NotOpenException, IOException {
        if (args.length < 2) {
            System.out.println("Usage: java PayloadExtractor <input.pcap> <output.bin>");
            return;
        }

        String pcapFile = args[0];
        String outputFile = args[1];

        PcapHandle handle = Pcaps.openOffline(pcapFile, PcapHandle.TimestampPrecision.NANO);
        FileOutputStream fos = new FileOutputStream(Paths.get(outputFile).toFile());

        PacketListener listener = packet -> {
            try {
                if (packet.contains(IpV4Packet.class) && packet.contains(TcpPacket.class)) {
                    TcpPacket tcpPacket = packet.get(TcpPacket.class);
                    if (tcpPacket != null && tcpPacket.getPayload() != null) {
                        byte[] payload = tcpPacket.getPayload().getRawData();
                        fos.write(payload);
                    }
                }
            } catch (IOException e) {
                System.err.println("Write error: " + e.getMessage());
            }
        };

        try {
            handle.loop(-1, listener);
        } catch (InterruptedException e) {
            System.err.println("Capture interrupted.");
        } finally {
            handle.close();
            fos.close();
        }

        System.out.println("Payload extraction complete.");
    }
}
