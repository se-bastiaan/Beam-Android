package com.github.se_bastiaan.beam.discovery;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;

import com.github.se_bastiaan.beam.control.ControlClient;
import com.github.se_bastiaan.beam.device.BeamDevice;
import com.github.se_bastiaan.beam.discovery.client.AirPlayDiscoveryClient;
import com.github.se_bastiaan.beam.discovery.client.GoogleCastDiscoveryClient;
import com.github.se_bastiaan.beam.discovery.client.SSDPDiscoveryClient;
import com.github.se_bastiaan.beam.util.Foreground;
import com.github.se_bastiaan.beam.util.ThreadUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class DiscoveryManager implements DiscoveryClientListener, Foreground.Listener {

    private static final String MULTICAST_TAG = "BeamDiscoveryManager";

    private Context context;

    private ConcurrentHashMap<String, BeamDevice> devices;

    private CopyOnWriteArrayList<DiscoveryClient> discoveryClients;
    private CopyOnWriteArrayList<DiscoveryManagerListener> discoveryListeners;

    private WifiManager.MulticastLock multicastLock;
    private BroadcastReceiver receiver;

    private boolean isBroadcastReceiverRegistered = false;

    private boolean searching = false;

    public DiscoveryManager(Context context) {
        this.context = context;
        Foreground.init(((Application) this.context)).addListener(this);

        devices = new ConcurrentHashMap<>(8, 0.75f, 2);

        discoveryClients = new CopyOnWriteArrayList<>();
        discoveryListeners = new CopyOnWriteArrayList<>();

        WifiManager wifiMgr = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        multicastLock = wifiMgr.createMulticastLock(MULTICAST_TAG);
        multicastLock.setReferenceCounted(true);

        receiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                    NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);

                    switch (networkInfo.getState()) {
                        case CONNECTED:
                            if (searching) {
                                for (DiscoveryClient provider : discoveryClients) {
                                    provider.restart();
                                }
                            }
                            break;

                        case DISCONNECTED:
                            for (DiscoveryClient provider : discoveryClients) {
                                provider.reset();
                            }

                            for (BeamDevice device : devices.values()) {
                                handleDeviceLoss(device);
                            }

                            devices.clear();
                            break;
                        case CONNECTING:
                            break;
                        case DISCONNECTING:
                            break;
                        case SUSPENDED:
                            break;
                        case UNKNOWN:
                            break;
                    }
                }
            }
        };

        registerBroadcastReceiver();
    }

    private void registerBroadcastReceiver() {
        if (!isBroadcastReceiverRegistered) {
            isBroadcastReceiverRegistered = true;

            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
            context.registerReceiver(receiver, intentFilter);
        }
    }

    private void unregisterBroadcastReceiver() {
        if (isBroadcastReceiverRegistered) {
            isBroadcastReceiverRegistered = false;

            context.unregisterReceiver(receiver);
        }
    }

    public void registerDefaultClients() {
        registerDiscoveryClient(GoogleCastDiscoveryClient.class);
        registerDiscoveryClient(SSDPDiscoveryClient.class);
        registerDiscoveryClient(AirPlayDiscoveryClient.class);
    }

    /**
     * Registers a DiscoveryClient with DiscoveryManager
     * @param discoveryClass Class for object that should discover devices
     */
    public void registerDiscoveryClient(Class<? extends DiscoveryClient> discoveryClass) {
        if (!DiscoveryClient.class.isAssignableFrom(discoveryClass))
            return;

        try {
            DiscoveryClient discoveryClient = null;

            for (DiscoveryClient dc : discoveryClients) {
                if (dc.getClass().isAssignableFrom(discoveryClass)) {
                    discoveryClient = dc;
                    break;
                }
            }

            if (discoveryClient == null) {
                Constructor<? extends DiscoveryClient> myConstructor = discoveryClass.getConstructor(Context.class);
                Object myObj = myConstructor.newInstance(context);
                discoveryClient = (DiscoveryClient) myObj;

                discoveryClient.addListener(this);
                discoveryClients.add(discoveryClient);
            }
        } catch (NoSuchMethodException | IllegalAccessException | RuntimeException | InvocationTargetException | InstantiationException e) {
            e.printStackTrace();
        }
    }

    /**
     * Unregisters a DiscoveryService with DiscoveryManager.
     *
     * @param discoveryClass Class for DiscoveryClient that is discovering devices
     */
    public void unregisterDiscoveryClient(Class<?> discoveryClass) {
        if (!DiscoveryClient.class.isAssignableFrom(discoveryClass)) {
            return;
        }

        try {
            DiscoveryClient discoveryClient = null;

            for (DiscoveryClient dc : discoveryClients) {
                if (dc.getClass().isAssignableFrom(discoveryClass)) {
                    discoveryClient = dc;
                    break;
                }
            }

            if (discoveryClient == null) {
                return;
            }

            discoveryClients.remove(discoveryClient);
        } catch (SecurityException | IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    /**
     * Listener which should receive discovery updates. It is not necessary to set this listener property unless you are implementing your own device picker. Connect SDK provides a default DevicePicker which acts as a DiscoveryManagerListener, and should work for most cases.
     *
     * If you have provided a capabilityFilters array, the listener will only receive update messages for ConnectableDevices which satisfy at least one of the CapabilityFilters. If no capabilityFilters array is provided, the listener will receive update messages for all ConnectableDevice objects that are discovered.
     */
    public void addListener(DiscoveryManagerListener listener) {
        // notify listener of all devices so far
        for (BeamDevice device: devices.values()) {
            listener.onDeviceAdded(this, device);
        }
        discoveryListeners.add(listener);
    }

    /**
     * Removes a previously added listener
     */
    public void removeListener(DiscoveryManagerListener listener) {
        discoveryListeners.remove(listener);
    }

    /**
     * Start scanning for devices on the local network.
     */
    public void start() {
        if (searching) {
            return;
        }

        if (discoveryClients == null) {
            return;
        }

        if (discoveryClients.size() == 0) {
            registerDefaultClients();
        }

        searching = true;
        multicastLock.acquire();

        ThreadUtil.runOnMainThread(new Runnable() {

            @Override
            public void run() {
                ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

                if (connManager.getActiveNetworkInfo().getType() == ConnectivityManager.TYPE_WIFI) {
                    for (DiscoveryClient provider : discoveryClients) {
                        provider.start();
                    }
                }
            }
        });
    }

    /**
     * Stop scanning for devices.
     */
    public void stop() {
        if (!searching)
            return;

        searching = false;

        for (DiscoveryClient provider : discoveryClients) {
            provider.stop();
        }

        if (multicastLock.isHeld()) {
            multicastLock.release();
        }

        for (String key : devices.keySet()) {
            BeamDevice device = devices.get(key);
            if (!device.isConnected()) {
                handleDeviceLoss(device);
                devices.remove(key);
            }
        }
    }

    private void handleDeviceAdd(BeamDevice device) {
        devices.put(device.getId(), device);

        for (DiscoveryManagerListener listener: discoveryListeners) {
            listener.onDeviceAdded(this, device);
        }
    }

    private void handleDeviceUpdate(BeamDevice device) {
        if (device.getId() != null && devices.containsKey(device.getId())) {
            for (DiscoveryManagerListener listener: discoveryListeners) {
                listener.onDeviceUpdated(this, device);
            }
        } else {
            handleDeviceAdd(device);
        }
    }

    private void handleDeviceLoss(BeamDevice device) {
        for (DiscoveryManagerListener listener : discoveryListeners) {
            listener.onDeviceRemoved(this, device);
        }
    }

    public Map<String, BeamDevice> getDevices() {
        return devices;
    }

    public Context getContext() {
        return context;
    }

    public List<DiscoveryClient> getDiscoveryClients() {
        return new ArrayList<>(discoveryClients);
    }

    @Override
    public void onDeviceAdded(DiscoveryClient client, BeamDevice device) {
        BeamDevice foundDevice = devices.get(device.getId());

        boolean isNew = foundDevice == null;

        if (isNew) {
            foundDevice = device;
        } else {
            if (!foundDevice.getName().equals(device.getName())) {
                foundDevice.setName(device.getName());
            }
        }

        foundDevice.setLastDetection(device.getLastDetection());

        handleDeviceUpdate(foundDevice);
    }

    @Override
    public void onDeviceRemoved(DiscoveryClient client, BeamDevice removedDevice) {
        if (devices.containsKey(removedDevice.getId())) {
            final BeamDevice device = devices.get(removedDevice.getId());

            if (device != null) {
                handleDeviceLoss(device);
            }
        }
    }

    @Override
    public void onBecameForeground() {
        start();
        registerBroadcastReceiver();
    }

    @Override
    public void onBecameBackground() {
        stop();
        unregisterBroadcastReceiver();
    }

}
