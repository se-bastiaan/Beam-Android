/*
 * Copyright (C) 2015-2016 Sébastiaan (github.com/se-bastiaan)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.se_bastiaan.beam.discovery.ssdp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;

public class SSDPClient {

    public static final String NEWLINE = "\r\n";

    public static final String MULTICAST_ADDRESS = "239.255.255.250";
    public static final int PORT = 1900;

    public static final String NOTIFY = "NOTIFY * HTTP/1.1";
    public static final String MSEARCH = "M-SEARCH * HTTP/1.1";
    public static final String OK = "HTTP/1.1 200 OK";

    public static final String ALIVE = "ssdp:alive";
    public static final String BYEBYE = "ssdp:byebye";
    public static final String UPDATE = "ssdp:update";

    MulticastSocket datagramSocket;
    MulticastSocket multicastSocket;

    SocketAddress multicastGroup;
    NetworkInterface networkInterface;
    InetAddress localInAddress;

    int timeout = 0;
    static int MX = 5;

    public SSDPClient(InetAddress source) throws IOException {
        this(source, new MulticastSocket(PORT), new MulticastSocket(null));
    }

    public SSDPClient(InetAddress source, MulticastSocket mcSocket, MulticastSocket dgSocket) throws IOException {
        localInAddress = source;
        multicastSocket = mcSocket;
        datagramSocket = dgSocket;

        multicastGroup = new InetSocketAddress(MULTICAST_ADDRESS, PORT);
        networkInterface = NetworkInterface.getByInetAddress(localInAddress);
        multicastSocket.joinGroup(multicastGroup, networkInterface);

        datagramSocket.setReuseAddress(true);
        datagramSocket.setTimeToLive(4);
        datagramSocket.bind(new InetSocketAddress(localInAddress, 0));
    }

    /**
     * Send a SSDP packet
     * @param data {@link String}
     * @throws IOException
     */
    public void send(String data) throws IOException {
        DatagramPacket dp = new DatagramPacket(data.getBytes(), data.length(), multicastGroup);

        datagramSocket.send(dp);
    }

    /**
     * Receive the SSDP response packet
     * @return {@link DatagramPacket}
     * @throws IOException
     */
    public DatagramPacket responseReceive() throws IOException {
        byte[] buf = new byte[1024];
        DatagramPacket dp = new DatagramPacket(buf, buf.length);

        datagramSocket.receive(dp);

        return dp;
    }

    /**
     * Receive a SSDP multicast response packet
     * @return {@link DatagramPacket}
     * @throws IOException
     */
    public DatagramPacket multicastReceive() throws IOException {
        byte[] buf = new byte[1024];
        DatagramPacket dp = new DatagramPacket(buf, buf.length);

        multicastSocket.receive(dp);

        return dp;
    }

    public boolean isConnected() {
        return datagramSocket != null && multicastSocket != null && datagramSocket.isConnected() && multicastSocket.isConnected();
    }

    /**
     * Close the socket connection
     */
    public void close() {
        if (multicastSocket != null) {
            try {
                multicastSocket.leaveGroup(multicastGroup, networkInterface);
            } catch (IOException e) {
                e.printStackTrace();
            }
            multicastSocket.close();
        }

        if (datagramSocket != null) {
            datagramSocket.disconnect();
            datagramSocket.close();
        }
    }

    public void setTimeout(int timeout) throws SocketException {
        this.timeout = timeout;
        datagramSocket.setSoTimeout(this.timeout);
    }

    public static String getSSDPSearchMessage(String ST) {
        StringBuilder sb = new StringBuilder();

        sb.append(MSEARCH + NEWLINE);
        sb.append("HOST: " + MULTICAST_ADDRESS + ":" + PORT + NEWLINE);
        sb.append("MAN: \"ssdp:discover\"" + NEWLINE);
        sb.append("ST: ").append(ST).append(NEWLINE);
        sb.append("MX: ").append(MX).append(NEWLINE);
        sb.append(NEWLINE);

        return sb.toString();
    }

}