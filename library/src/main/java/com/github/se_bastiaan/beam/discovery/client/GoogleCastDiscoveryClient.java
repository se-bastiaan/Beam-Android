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
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.support.v7.media.MediaRouter.RouteInfo;

import com.github.se_bastiaan.beam.device.GoogleCastDevice;
import com.github.se_bastiaan.beam.discovery.DiscoveryClient;
import com.github.se_bastiaan.beam.discovery.DiscoveryClientListener;
import com.github.se_bastiaan.beam.util.ThreadUtil;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;

import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class GoogleCastDiscoveryClient implements DiscoveryClient {

    private static final long ROUTE_REMOVE_INTERVAL = 3000;

    private MediaRouter mediaRouter;
    private MediaRouteSelector mediaRouteSelector;
    protected MediaRouter.Callback mediaRouterCallback;

    private List<String> removedUUID = new CopyOnWriteArrayList<String>();
    protected ConcurrentHashMap<String, GoogleCastDevice> foundDevices;
    protected CopyOnWriteArrayList<DiscoveryClientListener> clientListeners;

    private Timer removeRoutesTimer;

    private boolean isRunning = false;

    public GoogleCastDiscoveryClient(Context context) {
        context = context.getApplicationContext();
        mediaRouter = createMediaRouter(context);
        mediaRouterCallback = new MediaRouterCallback();

        foundDevices = new ConcurrentHashMap<>(8, 0.75f, 2);
        clientListeners = new CopyOnWriteArrayList<>();
    }

    protected MediaRouter createMediaRouter(Context context) {
        return MediaRouter.getInstance(context);
    }

    @Override
    public void start() {
        if (isRunning) 
            return;

        isRunning = true;

        if (mediaRouteSelector == null) {
            mediaRouteSelector = new MediaRouteSelector.Builder()
                    .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
                    .build();
        }

        rescan();
    }

    @Override
    public void stop() {
        isRunning = false;

        if (removeRoutesTimer != null) {
            removeRoutesTimer.cancel();
            removeRoutesTimer = null;
        }

        if (mediaRouter != null) {
            ThreadUtil.runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    mediaRouter.removeCallback(mediaRouterCallback);
                }
            });
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
        ThreadUtil.runOnMainThread(new Runnable() {

            @Override
            public void run() {
                mediaRouter.addCallback(mediaRouteSelector, mediaRouterCallback,
                        MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
            }
        });
    }

    @Override
    public void addListener(DiscoveryClientListener listener) {
        clientListeners.add(listener);
    }

    @Override
    public void removeListener(DiscoveryClientListener listener) {
        clientListeners.remove(listener);
    }

    private class MediaRouterCallback extends MediaRouter.Callback {

        @Override
        public void onRouteAdded(MediaRouter router, MediaRouter.RouteInfo route) {
            super.onRouteAdded(router, route);

            CastDevice castDevice = CastDevice.getFromBundle(route.getExtras());
            String uuid = castDevice.getDeviceId();

            removedUUID.remove(uuid);

            GoogleCastDevice foundDevice = foundDevices.get(uuid);

            boolean isNew = foundDevice == null;
            boolean listUpdateFlag = false;

            if (isNew) {
                foundDevice = new GoogleCastDevice(route);
                listUpdateFlag = true;
            } else {
                if (!foundDevice.getName().equals(castDevice.getFriendlyName())) {
                    foundDevice.setName(castDevice.getFriendlyName());
                    listUpdateFlag = true;
                }
            }

            foundDevice.setLastDetection(new Date().getTime());

            foundDevices.put(uuid, foundDevice);

            if (listUpdateFlag) {
                for (DiscoveryClientListener listener: clientListeners) {
                    listener.onDeviceAdded(GoogleCastDiscoveryClient.this, foundDevice);
                }
            }
        }

        @Override
        public void onRouteChanged(MediaRouter router, RouteInfo route) {
            super.onRouteChanged(router, route);

            CastDevice castDevice = CastDevice.getFromBundle(route.getExtras());
            String uuid = castDevice.getDeviceId();

            GoogleCastDevice foundDevice = foundDevices.get(uuid);

            boolean isNew = foundDevice == null;
            boolean listUpdateFlag = false;

            if (isNew) {
                foundDevice = new GoogleCastDevice(route);
                listUpdateFlag = true;
            } else {
                if (!foundDevice.getName().equals(castDevice.getFriendlyName())) {
                    foundDevice.setName(castDevice.getFriendlyName());
                    listUpdateFlag = true;
                }
            }

            foundDevice.setLastDetection(new Date().getTime());

            foundDevices.put(uuid, foundDevice);

            if (listUpdateFlag) {
                for (DiscoveryClientListener listener: clientListeners) {
                    listener.onDeviceAdded(GoogleCastDiscoveryClient.this, foundDevice);
                }
            }
        }

        @Override
        public void onRouteRemoved(final MediaRouter router, final RouteInfo route) {
            super.onRouteRemoved(router, route);

            CastDevice castDevice = CastDevice.getFromBundle(route.getExtras());
            String uuid = castDevice.getDeviceId();
            removedUUID.add(uuid);

            // Prevent immediate removing. There are some cases when service is removed and added
            // again after a second.
            if (removeRoutesTimer == null) {
                removeRoutesTimer = new Timer();
                removeRoutesTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        removeDevices(route);
                    }
                }, ROUTE_REMOVE_INTERVAL);
            }
        }

        @Override
        public void onRouteVolumeChanged(MediaRouter router, RouteInfo route) {
            super.onRouteVolumeChanged(router, route);
        }

        private void removeDevices(RouteInfo route) {
            for (String uuid : removedUUID) {
                final GoogleCastDevice device = foundDevices.get(uuid);
                if (device != null) {
                    ThreadUtil.runOnMainThread(new Runnable() {

                        @Override
                        public void run() {
                            for (DiscoveryClientListener listener : clientListeners) {
                                listener.onDeviceRemoved(GoogleCastDiscoveryClient.this, device);
                            }
                        }
                    });
                    foundDevices.remove(uuid);
                }
            }
            removedUUID.clear();
        }
    }

}