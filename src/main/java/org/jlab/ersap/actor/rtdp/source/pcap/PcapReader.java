package org.jlab.ersap.actor.rtdp.source.pcap;

import org.jnetpcap.Pcap;
import org.jnetpcap.PcapHeader;
import org.jnetpcap.packet.JPacket;
import org.jnetpcap.packet.JPacketHandler;
import org.jnetpcap.protocol.network.Ip4;
import org.jnetpcap.protocol.tcpip.Tcp;
import org.jnetpcap.protocol.tcpip.Udp;

import java.nio.charset.StandardCharsets;

public class PcapReader {
    public static void main(String[] args) {
        String filePath = "path/to/your.pcap"; // Update with your PCAP file path

        // Open the PCAP file
        final Pcap pcap = Pcap.openOffline(filePath, new StringBuilder());
        if (pcap == null) {
            System.err.println("Error opening pcap file.");
            return;
        }

        // Handlers for extracting protocols
        final Ip4 ip = new Ip4();
        final Tcp tcp = new Tcp();
        final Udp udp = new Udp();

        // Process each packet
        pcap.loop(Pcap.LOOP_INFINITE, new JPacketHandler<StringBuilder>() {
            @Override
            public void nextPacket(JPacket packet, StringBuilder errbuf) {
                if (packet.hasHeader(ip)) {
                    String srcIP = org.jnetpcap.packet.format.FormatUtils.ip(ip.source());
                    String dstIP = org.jnetpcap.packet.format.FormatUtils.ip(ip.destination());

                    if (packet.hasHeader(tcp)) {
                        int srcPort = tcp.source();
                        int dstPort = tcp.destination();
                        byte[] payload = tcp.getPayload();
                        System.out.printf("TCP Packet: %s:%d -> %s:%d | Payload: %s%n",
                                srcIP, srcPort, dstIP, dstPort, new String(payload, StandardCharsets.UTF_8));
                    } else if (packet.hasHeader(udp)) {
                        int srcPort = udp.source();
                        int dstPort = udp.destination();
                        byte[] payload = udp.getPayload();
                        System.out.printf("UDP Packet: %s:%d -> %s:%d | Payload: %s%n",
                                srcIP, srcPort, dstIP, dstPort, new String(payload, StandardCharsets.UTF_8));
                    }
                }
            }
        }, new StringBuilder());

        // Close the PCAP handle
        pcap.close();
    }
}
