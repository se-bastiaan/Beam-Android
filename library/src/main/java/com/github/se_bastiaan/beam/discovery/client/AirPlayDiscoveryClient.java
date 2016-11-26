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

import com.github.druk.rxdnssd.BonjourService;
import com.github.druk.rxdnssd.RxDnssd;
import com.github.druk.rxdnssd.RxDnssdEmbedded;
import com.github.se_bastiaan.beam.device.AirPlayDevice;
import com.github.se_bastiaan.beam.discovery.DiscoveryClient;
import com.github.se_bastiaan.beam.discovery.DiscoveryClientListener;
import com.github.se_bastiaan.beam.util.ThreadUtil;

import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

/**
 * Currently only discovers AirPlay devices
 */
public class AirPlayDiscoveryClient implements DiscoveryClient {

    private static final String SERVICE_TYPE = "_airplay._tcp";

    private Subscription subscription;
    private RxDnssd dnssd;

    private ConcurrentHashMap<String, AirPlayDevice> foundDevices;
    private CopyOnWriteArrayList<DiscoveryClientListener> clientListeners;

    private boolean isRunning = false;

    public AirPlayDiscoveryClient(Context context) {
        foundDevices = new ConcurrentHashMap<>(8, 0.75f, 2);

        clientListeners = new CopyOnWriteArrayList<>();

        dnssd = new RxDnssdEmbedded();
    }

    @Override
    public void start() {
        if (isRunning)
            return;

        isRunning = true;

        subscription = dnssd.browse(SERVICE_TYPE, "local.")
                .compose(dnssd.resolve())
                .compose(dnssd.queryRecords())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<BonjourService>() {
                    @Override
                    public void call(BonjourService bonjourService) {
                        if (bonjourService.isLost()) {
                            handleServiceLost(bonjourService);
                        } else {
                            handleServiceFound(bonjourService);
                        }
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        throwable.printStackTrace();
                    }
                });
    }

    @Override
    public void stop() {
        isRunning = false;

        if (subscription != null && !subscription.isUnsubscribed()) {
            subscription.unsubscribe();
        }
        subscription = null;
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
        restart();
    }

    @Override
    public void addListener(DiscoveryClientListener listener) {
        clientListeners.add(listener);
    }

    @Override
    public void removeListener(DiscoveryClientListener listener) {
        clientListeners.remove(listener);
    }

    private void handleServiceFound(BonjourService service) {
        String name = service.getServiceName();

        String key = getServiceKey(service);
        AirPlayDevice foundDevice = foundDevices.get(key);

        boolean isNew = foundDevice == null;
        boolean listUpdateFlag = false;

        if (isNew) {
            foundDevice = new AirPlayDevice(service);
            listUpdateFlag = true;
        }
        else {
            if (!foundDevice.getName().equals(name)) {
                foundDevice.setName(name);
                listUpdateFlag = true;
            }
        }

        foundDevice.setLastDetection(new Date().getTime());

        foundDevices.put(key, foundDevice);

        if (listUpdateFlag) {
            for (DiscoveryClientListener listener: clientListeners) {
                listener.onDeviceAdded(AirPlayDiscoveryClient.this, foundDevice);
            }
        }
    }

    private void handleServiceLost(BonjourService service) {
        final AirPlayDevice device = foundDevices.get(getServiceKey(service));

        if (device != null) {
            ThreadUtil.runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    for (DiscoveryClientListener listener : clientListeners) {
                        listener.onDeviceRemoved(AirPlayDiscoveryClient.this, device);
                    }
                }
            });
        }
    }

    private String getServiceKey(BonjourService service) {
        String key = "";
        if (service.getInet4Address() != null) {
            key += service.getInet4Address().getHostAddress();
        } else if (service.getInet6Address() != null) {
            key += service.getInet6Address().getHostAddress();
        }
        return key;
    }

}