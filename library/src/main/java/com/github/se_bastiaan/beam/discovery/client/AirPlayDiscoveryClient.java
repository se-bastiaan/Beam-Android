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
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;

import com.github.se_bastiaan.beam.device.AirPlayDevice;
import com.github.se_bastiaan.beam.discovery.DiscoveryClient;
import com.github.se_bastiaan.beam.discovery.DiscoveryClientListener;
import com.github.se_bastiaan.beam.discovery.nsd.RecordResolver;
import com.github.se_bastiaan.beam.util.ThreadUtil;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Currently only discovers AirPlay devices
 */
public class AirplayDiscoveryClient implements DiscoveryClient {

    private static final String SERVICE_TYPE = "_airplay._tcp.";
    private static final int RESOLVE_TIMEOUT = 10000;

    final NsdManager nsdManager;

    private ConcurrentHashMap<String, AirPlayDevice> foundDevices;
    private CopyOnWriteArrayList<DiscoveryClientListener> serviceListeners;

    private boolean isRunning = false;

    private NsdManager.DiscoveryListener discoveryListener = new NsdManager.DiscoveryListener() {
        @Override
        public void onStartDiscoveryFailed(String s, int i) {

        }

        @Override
        public void onStopDiscoveryFailed(String s, int i) {

        }

        @Override
        public void onDiscoveryStarted(String s) {

        }

        @Override
        public void onDiscoveryStopped(String s) {

        }

        @Override
        public void onServiceFound(NsdServiceInfo nsdServiceInfo) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                nsdManager.resolveService(nsdServiceInfo, resolveListener);
            } else {
                resolveService(nsdServiceInfo);
            }
        }

        @Override
        public void onServiceLost(NsdServiceInfo nsdServiceInfo) {
            String key = nsdServiceInfo.getHost().getHostAddress();

            final AirPlayDevice device = foundDevices.get(key);

            if (device != null) {
                ThreadUtil.runOnMainThread(new Runnable() {

                    @Override
                    public void run() {
                        for (DiscoveryClientListener listener : serviceListeners) {
                            listener.onDeviceRemoved(AirplayDiscoveryClient.this, device);
                        }
                    }
                });
            }

            if (foundDevices.containsKey(key)) {
                foundDevices.remove(key);
            }
        }
    };

    private NsdManager.ResolveListener resolveListener = new NsdManager.ResolveListener() {
        @Override
        public void onResolveFailed(NsdServiceInfo nsdServiceInfo, int i) {

        }

        @Override
        public void onServiceResolved(NsdServiceInfo serviceInfo) {
            String name = serviceInfo.getServiceName();
            final String ipAddress = serviceInfo.getHost().getHostAddress();

            AirPlayDevice foundDevice = foundDevices.get(ipAddress);

            boolean isNew = foundDevice == null;
            boolean listUpdateFlag = false;

            if (isNew) {
                foundDevice = new AirPlayDevice(serviceInfo, null);
                listUpdateFlag = true;
            }
            else {
                if (!foundDevice.getName().equals(name)) {
                    foundDevice.setName(name);
                    listUpdateFlag = true;
                }
            }

            foundDevice.setLastDetection(new Date().getTime());

            foundDevices.put(ipAddress, foundDevice);

            if (listUpdateFlag) {
                for (DiscoveryClientListener listener: serviceListeners) {
                    listener.onDeviceAdded(AirplayDiscoveryClient.this, foundDevice);
                }
            }
        }
    };

    public AirplayDiscoveryClient(Context context) {
        nsdManager = (NsdManager) context.getApplicationContext().getSystemService(Context.NSD_SERVICE);

        foundDevices = new ConcurrentHashMap<>(8, 0.75f, 2);

        serviceListeners = new CopyOnWriteArrayList<>();
    }

    @Override
    public void start() {
        if (isRunning)
            return;

        isRunning = true;

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
    }

    @Override
    public void stop() {
        isRunning = false;

        try {
            nsdManager.stopServiceDiscovery(discoveryListener);
        } catch (IllegalArgumentException e) {
            // service discovery not active on listener
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
        nsdManager.stopServiceDiscovery(discoveryListener);
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
    }

    @Override
    public void addListener(DiscoveryClientListener listener) {
        serviceListeners.add(listener);
    }

    @Override
    public void removeListener(DiscoveryClientListener listener) {
        serviceListeners.remove(listener);
    }

    private void resolveService(final NsdServiceInfo serviceInfo) {
        ThreadUtil.runInBackground(new Runnable() {
            @Override
            public void run() {
                try {
                    RecordResolver.Result result = RecordResolver.resolve(serviceInfo.getServiceName(), RESOLVE_TIMEOUT);

                    if (result.a == null && result.srv == null && result.txt == null) {
                        return;
                    }

                    String name = serviceInfo.getServiceName();
                    final String ipAddress = serviceInfo.getHost().getHostAddress();

                    AirPlayDevice foundDevice = foundDevices.get(ipAddress);

                    boolean isNew = foundDevice == null;
                    boolean listUpdateFlag = false;

                    if (isNew) {
                        foundDevice = new AirPlayDevice(serviceInfo, result);
                        listUpdateFlag = true;
                    }
                    else {
                        if (!foundDevice.getName().equals(name)) {
                            foundDevice.setName(name);
                            listUpdateFlag = true;
                        }
                    }

                    foundDevice.setLastDetection(new Date().getTime());

                    foundDevices.put(ipAddress, foundDevice);

                    if (listUpdateFlag) {
                        ThreadUtil.runOnMainThread(new Runnable() {
                            @Override
                            public void run() {
                                for (DiscoveryClientListener listener: serviceListeners) {
                                    listener.onDeviceAdded(AirplayDiscoveryClient.this, foundDevices.get(ipAddress));
                                }
                            }
                        });
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

}