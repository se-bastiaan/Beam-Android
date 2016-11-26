package com.github.se_bastiaan.beam.discovery.nsd;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.HashMap;
import java.util.Map;

public class RecordResolver {

    private static final short QTYPE_A   = 0x0001;
    private static final short QTYPE_PTR = 0x000c;
    private static final short QTYPE_TXT = 0x0010;
    private static final short QTYPE_SRV = 0x0021;

    private static final short QCLASS_INTERNET = 0x0001;
    private static final short CLASS_FLAG_UNICAST = (short) 0x8000;
    private static final int PORT = 5353;

    private static final String MULTICAST_GROUP_ADDRESS = "224.0.0.251";

    static byte[] queryPacket(String serviceName, int qclass, int... qtypes) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        dos.writeInt(0);
        dos.writeShort(qtypes.length);  // questions
        dos.writeShort(0);  // answers
        dos.writeShort(0);  // nscount
        dos.writeShort(0);  // arcount
        int fqdnPtr = -1;
        for (int qtype : qtypes) {
            if (fqdnPtr == -1) {
                fqdnPtr = dos.size();
                writeFQDN(serviceName, dos);
            } else {
                // packet compression, string is just a pointer to previous occurrence
                dos.write(0xc0 | (fqdnPtr >> 8));
                dos.write(fqdnPtr & 0xFF);
            }
            dos.writeShort(qtype);
            dos.writeShort(qclass);
        }
        dos.close();
        return bos.toByteArray();
    }

    /**
     * Ask for the A, SRV and TXT records of a particular service.
     * @param serviceName the name of service to query in mDNS, e.g.
     *                    {@code "device-1234._example._tcp.local"}
     * @param timeout duration in milliseconds to wait for an answer packet. If {@code 0}, this
     *                method will listen forever.
     * @return the reply packet's decoded answer data
     * @throws IOException
     */
    public static Result resolve(String serviceName, int timeout) throws IOException {
        if (timeout < 0) throw new IllegalArgumentException();
        InetAddress group = InetAddress.getByName(MULTICAST_GROUP_ADDRESS);
        MulticastSocket sock = new MulticastSocket();   // binds to a random free source port
        byte[] data = queryPacket(serviceName, QCLASS_INTERNET | CLASS_FLAG_UNICAST, QTYPE_A, QTYPE_SRV, QTYPE_TXT);
        DatagramPacket packet = new DatagramPacket(data, data.length, group, PORT);
        sock.setTimeToLive(255);
        sock.send(packet);
        byte[] buf = new byte[1024];
        packet = new DatagramPacket(buf, buf.length);
        Result result = new Result();
        long endTime = 0;
        if (timeout != 0) {
            endTime = System.currentTimeMillis() + timeout;
        }
        // records could be returned in different packets, so we have to loop
        // timeout applies to the acquisition of ALL packets
        while (result.a == null || result.srv == null || result.txt == null) {
            if (timeout != 0) {
                int remaining = (int) (endTime - System.currentTimeMillis());
                if (remaining <= 0) {
                    break;
                }
                sock.setSoTimeout(remaining);
            }
            sock.receive(packet);
            decode(packet.getData(), packet.getLength(), result);
        }
        return result;
    }

    private static void writeFQDN(String name, OutputStream out) throws IOException {
        for (String part : name.split("\\.")) {
            out.write(part.length());
            out.write(part.getBytes());
        }
        out.write(0);
    }

    public static class Record {
        /** Fully-Qualified Domain Name of the record. */
        public String fqdn;
        /** Time-to-live of the record, in seconds. */
        public int ttl;
    }

    /** DNS A record */
    public static class A extends Record {
        /** The IPv4 address in dot-decimal notation, e.g. {@code "192.168.1.100"} */
        public String ipaddr;
    }

    public static class SRV extends Record {
        public int priority, weight, port;
        /** Fully-Qualified Domain Name of the target service. */
        public String target;
    }

    public static class TXT extends Record {
        /** The content of the TXT record's key-value store decoded as a {@link Map} */
        public Map<String, String> dict;
    }

    /**
     * Represents the decoded content of the answer sections of an incoming packet.
     * When the corresponding data is present in an answer, fields will be initialized with
     * populated data structures. When no such answer is present in the packet, fields will be
     * {@code null}.
     */
    public static class Result {
        public A a;
        public SRV srv;
        public TXT txt;
    }

    static void decode(byte[] packet, int packetLength, Result result) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(packet, 0, packetLength));
        short transactionID = dis.readShort();
        short flags = dis.readShort();
        int questions = dis.readUnsignedShort();
        int answers = dis.readUnsignedShort();
        int authorityRRs = dis.readUnsignedShort();
        int additionalRRs = dis.readUnsignedShort();
        // decode the queries
        for (int i = 0; i < questions; i++) {
            String fqdn = decodeFQDN(dis, packet, packetLength);
            short type = dis.readShort();
            short qclass = dis.readShort();
        }
        // decode the answers
        for (int i = 0; i < answers + authorityRRs + additionalRRs; i++) {
            String fqdn = decodeFQDN(dis, packet, packetLength);
            short type = dis.readShort();
            short aclass = dis.readShort();
            int ttl = dis.readInt();
            int length = dis.readUnsignedShort();
            byte[] data = new byte[length];
            dis.readFully(data);
            Record record = null;
            switch (type) {
                case QTYPE_A:
                    record = result.a = decodeA(data);
                    break;
                case QTYPE_SRV:
                    record = result.srv = decodeSRV(data, packet, packetLength);
                    break;
                case QTYPE_PTR:
                    decodePTR(data, packet, packetLength);
                    break;
                case QTYPE_TXT:
                    record = result.txt = decodeTXT(data);
                    break;
                default:
                    break;
            }
            if (record != null) {
                record.fqdn = fqdn;
                record.ttl = ttl;
            }
        }
    }

    private static SRV decodeSRV(byte[] srvData, byte[] packetData, int packetLength) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(srvData));
        SRV srv = new SRV();
        srv.priority = dis.readUnsignedShort();
        srv.weight = dis.readUnsignedShort();
        srv.port = dis.readUnsignedShort();
        srv.target = decodeFQDN(dis, packetData, packetLength);
        return srv;
    }

    private static String typeString(short type) {
        switch (type) {
            case QTYPE_A:
                return "A";
            case QTYPE_PTR:
                return "PTR";
            case QTYPE_SRV:
                return "SRV";
            case QTYPE_TXT:
                return "TXT";
            default:
                return "Unknown";
        }
    }

    private static String decodePTR(byte[] ptrData, byte[] packet, int packetLength) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(ptrData));
        String fqdn = decodeFQDN(dis, packet, packetLength);
        return fqdn;
    }

    private static A decodeA(byte[] data) throws IOException {
        if (data.length < 4) throw new IOException("expected 4 bytes for IPv4 addr");
        A a = new A();
        a.ipaddr = (data[0] & 0xFF) + "." + (data[1] & 0xFF) + "." + (data[2] & 0xFF) + "." + (data[3] & 0xFF);
        return a;
    }

    private static TXT decodeTXT(byte[] data) throws IOException {
        TXT txt = new TXT();
        txt.dict = new HashMap<>();
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
        while (true) {
            int length;
            try {
                length = dis.readUnsignedByte();
            } catch (EOFException e) {
                return txt;
            }
            byte[] segmentBytes = new byte[length];
            dis.readFully(segmentBytes);
            String segment = new String(segmentBytes);
            int pos = segment.indexOf('=');
            String key, value = null;
            if (pos != -1) {
                key = segment.substring(0, pos);
                value = segment.substring(pos + 1);
            } else {
                key = segment;
            }
            if (!txt.dict.containsKey(key)) {
                // from RFC6763
                // If a client receives a TXT record containing the same key more than once, then
                // the client MUST silently ignore all but the first occurrence of that attribute."
                txt.dict.put(key, value);
            }
        }
    }

    private static String decodeFQDN(DataInputStream dis, byte[] packet, int packetLength) throws IOException {
        StringBuilder result = new StringBuilder();
        boolean dot = false;
        while (true) {
            int pointerHopCount = 0;
            int length;
            while (true) {
                length = dis.readUnsignedByte();
                if (length == 0) return result.toString();
                if ((length & 0xc0) == 0xc0) {
                    // this is a compression method, the remainder of the string is a pointer to elsewhere in the packet
                    // adjust the stream boundary and repeat processing
                    if ((++pointerHopCount) * 2 >= packetLength) {
                        // We must have visited one of the possible pointers more than once => cycle
                        // this doesn't add to the domain length, but decoding would be non-terminating
                        throw new IOException("cyclic empty references in domain name");
                    }
                    length &= 0x3f;
                    int offset = (length << 8) | dis.readUnsignedByte();
                    dis = new DataInputStream(new ByteArrayInputStream(packet, offset, packetLength - offset));
                } else {
                    break;
                }
            }
            byte[] segment = new byte[length];
            dis.readFully(segment);
            if (dot) result.append('.');
            dot = true;
            result.append(new String(segment));
            if (result.length() > packetLength) {
                // If we get here, we must be following cyclic references, since non-cyclic
                // references can't encode a domain name longer than the total length of the packet.
                // The domain name would be infinitely long, so abort now rather than consume
                // maximum heap.
                throw new IOException("cyclic non-empty references in domain name");
            }
        }
    }

}
