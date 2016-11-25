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

import com.github.se_bastiaan.beam.device.AirPlayDevice;
import com.github.se_bastiaan.beam.discovery.DiscoveryClient;
import com.github.se_bastiaan.beam.discovery.DiscoveryClientListener;
import com.github.se_bastiaan.beam.util.NetworkUtil;
import com.github.se_bastiaan.beam.util.ThreadUtil;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;

/**
 * Currently only discovers AirPlay devices
 */
public class AirplayDiscoveryClient implements DiscoveryClient {

    private static final String SERVICE_TYPE = "_airplay._tcp.local.";

    private JmDNS jmdns;
    private InetAddress srcAddress;

    private JmDNSThread jmdnsThread;
    private Timer scanTimer;

    private ConcurrentHashMap<String, AirPlayDevice> foundDevices;
    private CopyOnWriteArrayList<DiscoveryClientListener> clientListeners;

    private boolean isRunning = false;

    private ServiceListener jmdnsListener = new ServiceListener() {

        @Override
        public void serviceResolved(ServiceEvent ev) {
            if (ev.getInfo().getInet4Addresses().length <= 0) {
                // Currently, we only support ipv4
                return;
            }

            String ipAddress = ev.getInfo().getInetAddresses()[0].getHostAddress();
            String friendlyName = ev.getInfo().getName();

            AirPlayDevice foundDevice = foundDevices.get(ipAddress);

            boolean isNew = foundDevice == null;
            boolean listUpdateFlag = false;

            if (isNew) {
                foundDevice = new AirPlayDevice(ev.getInfo());
                listUpdateFlag = true;
            } else {
                if (!foundDevice.getName().equals(friendlyName)) {
                    foundDevice.setName(friendlyName);
                    listUpdateFlag = true;
                }
            }

            foundDevice.setLastDetection(new Date().getTime());

            foundDevices.put(ipAddress, foundDevice);

            if (listUpdateFlag) {
                for (DiscoveryClientListener listener : clientListeners) {
                    listener.onDeviceAdded(AirplayDiscoveryClient.this, foundDevice);
                }
            }
        }

        @Override
        public void serviceRemoved(ServiceEvent ev) {
            @SuppressWarnings("deprecation")
            String uuid = ev.getInfo().getHostAddress();
            final AirPlayDevice device = foundDevices.get(uuid);

            if (device != null) {
                ThreadUtil.runOnMainThread(new Runnable() {

                    @Override
                    public void run() {
                        for (DiscoveryClientListener listener : clientListeners) {
                            listener.onDeviceRemoved(AirplayDiscoveryClient.this, device);
                        }
                    }
                });
            }
        }

        @Override
        public void serviceAdded(final ServiceEvent event) {
            // Required to force serviceResolved to be called again
            // (after the first search)
            jmdns.requestServiceInfo(event.getType(), event.getName(), 30000);
        }
    };

    public AirplayDiscoveryClient(Context context) {
        foundDevices = new ConcurrentHashMap<>(8, 0.75f, 2);

        clientListeners = new CopyOnWriteArrayList<>();

        try {
            srcAddress = NetworkUtil.getIpAddress(context);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        jmdnsThread = new JmDNSThread();
    }

    @Override
    public void start() {
        if (isRunning)
            return;

        isRunning = true;

        jmdnsThread.start();
    }

    @Override
    public void stop() {
        isRunning = false;

        if (scanTimer != null) {
            scanTimer.cancel();
            scanTimer = null;
        }

        if (jmdns != null) {
            try {
                jmdns.removeServiceListener(SERVICE_TYPE, jmdnsListener);
                jmdns.close();
                jmdnsThread.interrupt();
            } catch (Exception e) {
                e.printStackTrace();
            }
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
        foundDevices.clear();
    }

    @Override
    public void rescan() {
        if (jmdns != null) {
            try {
                jmdns.removeServiceListener(SERVICE_TYPE, jmdnsListener);
                jmdns.close();
                jmdnsThread.interrupt();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        jmdnsThread = new JmDNSThread();
        jmdnsThread.start();
    }

    @Override
    public void addListener(DiscoveryClientListener listener) {
        clientListeners.add(listener);
    }

    @Override
    public void removeListener(DiscoveryClientListener listener) {
        clientListeners.remove(listener);
    }

    private class JmDNSThread extends Thread {
        @Override
        public void run() {
            try {
                if (srcAddress == null) {
                    return;
                }

                jmdns = JmDNS.create(srcAddress);
                jmdns.addServiceListener(SERVICE_TYPE, jmdnsListener);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}