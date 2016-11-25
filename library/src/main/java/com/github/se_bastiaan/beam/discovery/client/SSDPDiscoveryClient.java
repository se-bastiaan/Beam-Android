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

package com.github.se_bastiaan.beam.discovery.client;

import android.content.Context;

import com.github.se_bastiaan.beam.device.DLNADevice;
import com.github.se_bastiaan.beam.discovery.DiscoveryClient;
import com.github.se_bastiaan.beam.discovery.DiscoveryClientListener;
import com.github.se_bastiaan.beam.discovery.ssdp.SSDPClient;
import com.github.se_bastiaan.beam.discovery.ssdp.SSDPDevice;
import com.github.se_bastiaan.beam.discovery.ssdp.SSDPPacket;
import com.github.se_bastiaan.beam.discovery.ssdp.Service;
import com.github.se_bastiaan.beam.util.NetworkUtil;
import com.github.se_bastiaan.beam.util.ThreadUtil;

import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;

/**
 * Currently only disovers DLNA devices
 */
public class SSDPDiscoveryClient implements DiscoveryClient {

    private Context context;

    private CopyOnWriteArrayList<DiscoveryClientListener> clientListeners;

    private ConcurrentHashMap<String, DLNADevice> foundServices = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, DLNADevice> discoveredDevices = new ConcurrentHashMap<>();

    private static final String SERVICE_FILTER = "urn:schemas-upnp-org:device:MediaRenderer:1";
    private static final String SERVICE_ID = "urn:schemas-upnp-org:device:MediaRenderer:1";

    private SSDPClient ssdpClient;

    private Timer scanTimer;

    private Pattern uuidReg;

    private Thread responseThread;
    private Thread notifyThread;

    private boolean isRunning = false;

    public SSDPDiscoveryClient(Context context) {
        this.context = context;

        uuidReg = Pattern.compile("(?<=uuid:)(.+?)(?=(::)|$)");

        clientListeners = new CopyOnWriteArrayList<>();
    }

    private void openSocket() {
        if (ssdpClient != null && ssdpClient.isConnected())
            return;

        try {
            InetAddress source = NetworkUtil.getIpAddress(context);
            if (source == null)
                return;

            ssdpClient = createSocket(source);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected SSDPClient createSocket(InetAddress source) throws IOException {
        return new SSDPClient(source);
    }

    @Override
    public void start() {
        if (isRunning)
            return;

        isRunning = true;

        openSocket();

        scanTimer = new Timer();
        scanTimer.schedule(new TimerTask() {

            @Override
            public void run() {
                sendSearch();
            }
        }, 100, RESCAN_INTERVAL);

        responseThread = new Thread(responseHandler);
        notifyThread = new Thread(respNotifyHandler);

        responseThread.start();
        notifyThread.start();
    }

    public void sendSearch() {
        List<String> killKeys = new ArrayList<String>();

        long killPoint = new Date().getTime() - TIMEOUT;

        for (String key : foundServices.keySet()) {
            DLNADevice service = foundServices.get(key);
            if (service == null || service.getLastDetection() < killPoint) {
                killKeys.add(key);
            }
        }

        for (String key : killKeys) {
            final DLNADevice service = foundServices.get(key);

            if (service != null) {
                notifyListenersOfLostService(service);
            }

            if (foundServices.containsKey(key))
                foundServices.remove(key);
        }

        rescan();
    }

    @Override
    public void stop() {
        isRunning = false;

        if (scanTimer != null) {
            scanTimer.cancel();
            scanTimer = null;
        }

        if (responseThread != null) {
            responseThread.interrupt();
            responseThread = null;
        }

        if (notifyThread != null) {
            notifyThread.interrupt();
            notifyThread = null;
        }

        if (ssdpClient != null) {
            ssdpClient.close();
            ssdpClient = null;
        }
    }

    @Override
    public void restart() {
        stop();
        start();
    }

    @Override
    public void reset() {
        stop();
        foundServices.clear();
        discoveredDevices.clear();
    }

    @Override
    public void rescan() {
        final String message = SSDPClient.getSSDPSearchMessage(SERVICE_FILTER);

        Timer timer = new Timer();
            /* Send 3 times like WindowsMedia */
        for (int i = 0; i < 3; i++) {
            TimerTask task = new TimerTask() {

                @Override
                public void run() {
                    try {
                        if (ssdpClient != null)
                            ssdpClient.send(message);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            };

            timer.schedule(task, i * 1000);
        }
    }

    private Runnable responseHandler = new Runnable() {
        @Override
        public void run() {
            while (ssdpClient != null) {
                try {
                    handleSSDPPacket(new SSDPPacket(ssdpClient.responseReceive()));
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                } catch (RuntimeException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }
    };

    private Runnable respNotifyHandler = new Runnable() {
        @Override
        public void run() {
            while (ssdpClient != null) {
                try {
                    handleSSDPPacket(new SSDPPacket(ssdpClient.multicastReceive()));
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                } catch (RuntimeException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }
    };

    private void handleSSDPPacket(SSDPPacket ssdpPacket) {
        if (ssdpPacket == null || ssdpPacket.getData().size() == 0 || ssdpPacket.getType() == null) {
            return;
        }

        String serviceFilter = ssdpPacket.getData().get(ssdpPacket.getType().equals(SSDPClient.NOTIFY) ? "NT" : "ST");

        if (serviceFilter == null || SSDPClient.MSEARCH.equals(ssdpPacket.getType())) {
            return;
        }

        String usnKey = ssdpPacket.getData().get("USN");

        if (usnKey == null || usnKey.length() == 0) {
            return;
        }

        Matcher m = uuidReg.matcher(usnKey);

        if (!m.find()) {
            return;
        }

        String uuid = m.group();

        if (SSDPClient.BYEBYE.equals(ssdpPacket.getData().get("NTS"))) {
            final DLNADevice service = foundServices.get(uuid);

            if (service != null) {
                foundServices.remove(uuid);

                notifyListenersOfLostService(service);
            }
        } else {
            String location = ssdpPacket.getData().get("LOCATION");

            if (location == null || location.length() == 0)
                return;

            DLNADevice foundDevice = foundServices.get(uuid);
            DLNADevice discoveredDevices = this.discoveredDevices.get(uuid);

            boolean isNew = foundDevice == null && discoveredDevices == null;

            if (isNew) {
                foundDevice = new DLNADevice(uuid);
                foundDevice.setIpAddress(ssdpPacket.getDatagramPacket().getAddress().getHostAddress());
                foundDevice.setPort(3001);

                this.discoveredDevices.put(uuid, foundDevice);

                getLocationData(location, uuid);
            }

            if (foundDevice != null) {
                foundDevice.setLastDetection(new Date().getTime());
            }
        }
    }

    private void getLocationData(final String location, final String uuid) {
        try {
            getLocationData(new URL(location), uuid);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void getLocationData(final URL location, final String uuid) {
        ThreadUtil.runInBackground(new Runnable() {
            @Override
            public void run() {
                SSDPDevice ssdpDevice = null;
                try {
                    ssdpDevice = new SSDPDevice(location, SERVICE_FILTER);
                } catch (IOException | ParserConfigurationException | SAXException e) {
                    e.printStackTrace();
                }

                if (ssdpDevice != null && containsRequiredService(ssdpDevice.serviceList)) {
                    ssdpDevice.UUID = uuid;
                    final DLNADevice device = discoveredDevices.get(uuid);

                    if (device != null) {
                        device.setName(ssdpDevice.friendlyName);
                        device.setModel(ssdpDevice.modelName);

                        device.setServiceList(ssdpDevice.serviceList);
                        device.setPort(ssdpDevice.port);

                        foundServices.put(uuid, device);

                        notifyListenersOfNewService(device);
                    }
                }

                discoveredDevices.remove(uuid);
            }
        }, true);

    }

    private void notifyListenersOfNewService(final DLNADevice device) {
        ThreadUtil.runOnMainThread(new Runnable() {
            @Override
            public void run() {
                for (DiscoveryClientListener listener : clientListeners) {
                    listener.onDeviceAdded(SSDPDiscoveryClient.this, device);
                }
            }
        });
    }

    private void notifyListenersOfLostService(final DLNADevice device) {
        ThreadUtil.runOnMainThread(new Runnable() {
            @Override
            public void run() {
                for (DiscoveryClientListener listener : clientListeners) {
                    listener.onDeviceRemoved(SSDPDiscoveryClient.this, device);
                }
            }
        });
    }

    private boolean containsRequiredService(List<Service> services) {
        for (Service service : services) {
            if (service.serviceId.equalsIgnoreCase("urn:upnp-org:serviceId:AVTransport")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void addListener(DiscoveryClientListener listener) {
        clientListeners.add(listener);
    }

    @Override
    public void removeListener(DiscoveryClientListener listener) {
        clientListeners.remove(listener);
    }
    
}