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

package com.github.se_bastiaan.beam;

import android.content.Context;

import com.github.se_bastiaan.beam.control.ControlManager;
import com.github.se_bastiaan.beam.control.ControlManagerListener;
import com.github.se_bastiaan.beam.device.BeamDevice;
import com.github.se_bastiaan.beam.discovery.DiscoveryManager;
import com.github.se_bastiaan.beam.discovery.DiscoveryManagerListener;

import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class BeamManager implements DiscoveryManagerListener, ControlManagerListener {

    private static BeamManager instance;

    private DiscoveryManager discoveryManager;
    private ControlManager controlManager;
    private CopyOnWriteArrayList<BeamDiscoveryListener> discoveryListeners;
    private CopyOnWriteArrayList<BeamControlListener> controlListeners;

    public static synchronized BeamManager init(Context context) {
        instance = new BeamManager(context.getApplicationContext());
        return instance;
    }

    public static BeamManager getInstance() {
        if (instance == null) {
            throw new Error("Call BeamManager.init(Context) first");
        }
        return instance;
    }

    private BeamManager(Context context) {
        discoveryListeners = new CopyOnWriteArrayList<>();
        controlListeners = new CopyOnWriteArrayList<>();

        discoveryManager = new DiscoveryManager(context);
        discoveryManager.addListener(this);
        controlManager = new ControlManager(context);
        controlManager.addListener(this);
    }

    public DiscoveryManager getDiscoveryManager() {
        return discoveryManager;
    }

    public ControlManager getControlManager() {
        return controlManager;
    }

    public void addDiscoveryListener(BeamDiscoveryListener listener) {
        discoveryListeners.add(listener);
    }

    public void removeDiscoveryListener(BeamDiscoveryListener listener) {
        discoveryListeners.remove(listener);
    }

    public void addControlListener(BeamControlListener listener) {
        controlListeners.add(listener);
    }

    public void removeControlListener(BeamControlListener listener) {
        controlListeners.remove(listener);
    }

    public void loadMedia(MediaData mediaData) {
        controlManager.loadMedia(mediaData);
    }

    public void connect(BeamDevice device) {
        controlManager.connect(device);
    }

    public void disconnect() {
        controlManager.disconnect();
    }

    public void play() {
        controlManager.play();
    }

    public void pause() {
        controlManager.pause();
    }

    public void seek(long position) {
        controlManager.seek(position);
    }

    public void stop() {
        controlManager.stop();
    }

    public void setVolume(float volume) {
        controlManager.setVolume(volume);
    }

    public boolean canControlVolume() {
        return controlManager.canControlVolume();
    }

    public boolean isConnected() {
        return controlManager.isConnected();
    }

    public Map<String, BeamDevice> getDevices() {
        return discoveryManager.getDevices();
    }

    @Override
    public void onDeviceAdded(DiscoveryManager manager, BeamDevice device) {
        for (BeamDiscoveryListener listener : discoveryListeners) {
            listener.onDeviceAdded(this, device);
        }
    }

    @Override
    public void onDeviceRemoved(DiscoveryManager manager, BeamDevice device) {
        for (BeamDiscoveryListener listener : discoveryListeners) {
            listener.onDeviceRemoved(this, device);
        }
    }

    @Override
    public void onDeviceUpdated(DiscoveryManager manager, BeamDevice device) {
        for (BeamDiscoveryListener listener : discoveryListeners) {
            listener.onDeviceUpdated(this, device);
        }
    }

    @Override
    public void onConnected(ControlManager manager, BeamDevice device) {
        for (BeamControlListener listener : controlListeners) {
            listener.onConnected(this, device);
        }
    }

    @Override
    public void onDisconnected(ControlManager manager) {
        for (BeamControlListener listener : controlListeners) {
            listener.onDisconnected(this);
        }
    }

    @Override
    public void onVolumeChanged(ControlManager manager, double value, boolean isMute) {
        for (BeamControlListener listener : controlListeners) {
            listener.onVolumeChanged(this, value, isMute);
        }
    }

    @Override
    public void onPlayBackChanged(ControlManager manager, boolean isPlaying, long position, long duration) {
        for (BeamControlListener listener : controlListeners) {
            listener.onPlayBackChanged(this, isPlaying, position, duration);
        }
    }
}
