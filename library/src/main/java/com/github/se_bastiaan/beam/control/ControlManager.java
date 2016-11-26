package com.github.se_bastiaan.beam.control;

import android.content.Context;

import com.github.se_bastiaan.beam.MediaData;
import com.github.se_bastiaan.beam.control.client.AirPlayControlClient;
import com.github.se_bastiaan.beam.control.client.DLNAControlClient;
import com.github.se_bastiaan.beam.control.client.GoogleCastControlClient;
import com.github.se_bastiaan.beam.device.BeamDevice;
import com.github.se_bastiaan.beam.util.ThreadUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CopyOnWriteArrayList;

public class ControlManager implements ControlClientListener {

    private Context context;
    private BeamDevice currentDevice;
    private ControlClient currentClient;

    private CopyOnWriteArrayList<ControlClient> controlClients;
    private CopyOnWriteArrayList<ControlManagerListener> controlListeners;

    public ControlManager(Context context) {
        this.context = context;
        this.controlListeners = new CopyOnWriteArrayList<>();
        this.controlClients = new CopyOnWriteArrayList<>();
    }

    public void registerDefaultClients() {
        registerControlClient(GoogleCastControlClient.class);
        registerControlClient(DLNAControlClient.class);
        registerControlClient(AirPlayControlClient.class);
    }

    /**
     * Registers a DiscoveryClient with DiscoveryManager
     * @param controlClass Class for object that should discover devices
     */
    public void registerControlClient(Class<? extends ControlClient> controlClass) {
        if (!ControlClient.class.isAssignableFrom(controlClass))
            return;

        try {
            ControlClient controlClient = null;

            for (ControlClient cc : controlClients) {
                if (cc.getClass().isAssignableFrom(controlClass)) {
                    controlClient = cc;
                    break;
                }
            }

            if (controlClient == null) {
                Constructor<? extends ControlClient> myConstructor = controlClass.getConstructor(Context.class);
                Object myObj = myConstructor.newInstance(context);
                controlClient = (ControlClient) myObj;

                controlClient.addListener(this);
                controlClients.add(controlClient);
            }
        } catch (NoSuchMethodException | IllegalAccessException | RuntimeException | InvocationTargetException | InstantiationException e) {
            e.printStackTrace();
        }
    }

    /**
     * Unregisters a DiscoveryService with DiscoveryManager.
     *
     * @param controlClass Class for DiscoveryClient that is discovering devices
     */
    public void unregisterControlClient(Class<?> controlClass) {
        if (!ControlClient.class.isAssignableFrom(controlClass)) {
            return;
        }

        try {
            ControlClient controlClient = null;

            for (ControlClient cc : controlClients) {
                if (cc.getClass().isAssignableFrom(controlClass)) {
                    controlClient = cc;
                    break;
                }
            }

            if (controlClient == null) {
                return;
            }

            controlClients.remove(controlClient);
        } catch (SecurityException | IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    public void loadMedia(MediaData mediaData) {
        if (currentClient == null) {
            throw new IllegalStateException("Not connected to device");
        }

        currentClient.loadMedia(mediaData);
    }

    public void connect(BeamDevice device) {
        if (currentClient != null) {
            throw new IllegalStateException("Already connected to device");
        }
        
        if (controlClients == null) {
            return;
        }

        if (controlClients.size() == 0) {
            registerDefaultClients();
        }

        for (ControlClient client : controlClients) {
            if (client.canHandleDevice(device)) {
                currentClient = client;
                currentDevice = device;
                currentDevice.setConnected(true);
                break;
            }
        }

        if (currentClient != null) {
            currentClient.connect(device);
        }
    }

    public void disconnect() {
        if (currentClient == null) {
            throw new IllegalStateException("Not connected to device");
        }

        currentDevice.setConnected(false);
        currentClient.disconnect();

        currentDevice = null;
        currentClient = null;
    }

    public void play() {
        if (currentClient == null) {
            throw new IllegalStateException("Not connected to device");
        }

        currentClient.play();
    }

    public void pause() {
        if (currentClient == null) {
            throw new IllegalStateException("Not connected to device");
        }

        currentClient.pause();
    }

    public void seek(long position) {
        if (currentClient == null) {
            throw new IllegalStateException("Not connected to device");
        }

        if (position < 0) {
            position = 0;
        }

        currentClient.seek(position);
    }

    public void stop() {
        if (currentClient == null) {
            throw new IllegalStateException("Not connected to device");
        }

        currentClient.stop();
    }

    public void setVolume(float volume) {
        if (currentClient == null) {
            throw new IllegalStateException("Not connected to device");
        }

        currentClient.setVolume(volume);
    }

    public boolean canControlVolume() {
        if (currentClient == null) {
            throw new IllegalStateException("Not connected to device");
        }

        return currentClient.canControlVolume();
    }

    public boolean isConnected() {
        return currentClient != null;
    }

    public void addListener(ControlManagerListener listener) {
        controlListeners.add(listener);
    }

    public void removeListener(ControlManagerListener listener) {
        controlListeners.remove(listener);
    }

    @Override
    public void onConnected(ControlClient client, final BeamDevice device) {
        ThreadUtil.runOnMainThread(new Runnable() {
            @Override
            public void run() {
                for (ControlManagerListener listener : controlListeners) {
                    listener.onConnected(ControlManager.this, device);
                }
            }
        });
    }

    @Override
    public void onDisconnected(ControlClient client) {
        ThreadUtil.runOnMainThread(new Runnable() {
            @Override
            public void run() {
                for (ControlManagerListener listener : controlListeners) {
                    listener.onDisconnected(ControlManager.this);
                }
            }
        });
    }

    @Override
    public void onVolumeChanged(ControlClient client, final double value, final boolean isMute) {
        ThreadUtil.runOnMainThread(new Runnable() {
            @Override
            public void run() {
                for (ControlManagerListener listener : controlListeners) {
                    listener.onVolumeChanged(ControlManager.this, value, isMute);
                }
            }
        });
    }

    @Override
    public void onPlayBackChanged(ControlClient client, final boolean isPlaying, final long position, final long duration) {
        ThreadUtil.runOnMainThread(new Runnable() {
            @Override
            public void run() {
                for (ControlManagerListener listener : controlListeners) {
                    listener.onPlayBackChanged(ControlManager.this, isPlaying, position, duration);
                }
            }
        });
    }

}
