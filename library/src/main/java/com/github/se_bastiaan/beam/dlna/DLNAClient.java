package com.github.se_bastiaan.beam.dlna;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import org.fourthline.cling.android.AndroidUpnpService;
import org.fourthline.cling.model.action.ActionInvocation;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.model.meta.Device;
import org.fourthline.cling.model.meta.LocalDevice;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.registry.DefaultRegistryListener;
import org.fourthline.cling.registry.Registry;
import org.fourthline.cling.support.avtransport.callback.Pause;
import org.fourthline.cling.support.avtransport.callback.Play;
import org.fourthline.cling.support.avtransport.callback.Seek;
import org.fourthline.cling.support.avtransport.callback.SetAVTransportURI;
import org.fourthline.cling.support.avtransport.callback.Stop;

import java.util.ArrayList;
import java.util.List;

import com.github.se_bastiaan.beam.BaseBeamClient;
import com.github.se_bastiaan.beam.BeamDevice;
import com.github.se_bastiaan.beam.BeamListener;
import com.github.se_bastiaan.beam.logger.Logger;
import com.github.se_bastiaan.beam.model.Playback;

public class DLNAClient extends BaseBeamClient {

    private final String TAG = getClass().getCanonicalName();

    private Context context;
    private BeamListener listener;
    private AndroidUpnpService upnpService;
    private DlnaRegistryListener registryListener = new DlnaRegistryListener();
    private List<DLNADevice> discoveredDevices;
    private DLNADevice currentDevice;

    public DLNAClient(Context context, BeamListener listener) {
        this.context = context;
        discoveredDevices = new ArrayList<>();
        this.listener = listener;

        Intent serviceIntent = new Intent(context, DLNAService.class);
        context.getApplicationContext().bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * Call on close
     */
    public void destroy() {
        if(upnpService != null) {
            upnpService.getRegistry().removeListener(registryListener);
            context.getApplicationContext().unbindService(serviceConnection);
        }
    }

    /**
     * @param context New context for future use
     */
    public void setContext(Context context) {
        this.context = context;
    }

    @Override
    public void loadMedia(Playback playback, String location, float position) {
        if (getAVTransportService() == null)
            return;

        stop();

        DLNAMetaData trackMetaData = new DLNAMetaData(playback.videoId, playback.title, "", "", playback.image, location, "object.item.videoItem");

        upnpService.getControlPoint().execute(new SetAVTransportURI(getAVTransportService(), location, trackMetaData.getXML()) {
            @Override
            public void success(ActionInvocation invocation) {
                super.success(invocation);
                Logger.d(TAG, "DLNA: URI succesfully set!");
                play();
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse response, String message) {
                Logger.d(TAG, "DLNA: Failed to set uri: " + message);
            }
        });
    }

    @Override
    public void play() {
        if (getAVTransportService() == null)
            return;

        upnpService.getControlPoint().execute(new Play(getAVTransportService()) {
            @Override
            public void success(ActionInvocation invocation) {
                Logger.d(TAG, "DLNA: Success playing!");
                // TODO update player state
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse response, String message) {
                Logger.d(TAG, "DLNA: Failed to play: " + message);
            }
        });
    }

    @Override
    public void pause() {
        if (getAVTransportService() == null)
            return;

        upnpService.getControlPoint().execute(new Pause(getAVTransportService()) {
            @Override
            public void success(ActionInvocation invocation) {
                Logger.d(TAG, "DLNA: Successfully paused!");
                // TODO update player state
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse response, String message) {
                Logger.d(TAG, "DLNA: Failed to pause: " + message);
            }
        });
    }

    @Override
    public void seek(float position) {
        if (getAVTransportService() == null)
            return;

        upnpService.getControlPoint().execute(new Seek(getAVTransportService(), Float.toString(position)) {
            @Override
            public void success(ActionInvocation invocation) {
                Logger.d(TAG, "DLNA: Successfully sought!");
                // TODO update player state
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse response, String message) {
                Logger.d(TAG, "DLNA: Failed to seek: " + message);
            }
        });
    }

    @Override
    public void stop() {
        if (getAVTransportService() == null)
            return;

        upnpService.getControlPoint().execute(new Stop(getAVTransportService()) {
            @Override
            public void success(ActionInvocation invocation) {
                Logger.d(TAG, "DLNA: Successfully stopped!");
                // TODO update player state
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse response, String message) {
                Logger.d(TAG, "DLNA: Failed to stop: " + message);
            }
        });
    }

    @Override
    public void connect(BeamDevice device) {
        if(device != currentDevice && currentDevice != null) {
            disconnect();
        }
        currentDevice = (DLNADevice) device;
        listener.onConnected(currentDevice);
    }

    @Override
    public void disconnect() {
        stop();
        currentDevice = null;
        listener.onDisconnected();
    }

    @Override
    public void setVolume(float volume) {
        // Can't control volume (yet), so do nothing
    }

    @Override
    public boolean canControlVolume() {
        return false;
    }

    private Service getAVTransportService() {
        if(currentDevice == null) return null;
        return currentDevice.getAVTransportService();
    }

    private Service getRenderingControlService() {
        if(currentDevice == null) return null;
        return currentDevice.getRenderingControlService();
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder service) {
            upnpService = (AndroidUpnpService) service;

            discoveredDevices.clear();
            upnpService.getRegistry().addListener(registryListener);

            for (Device device : upnpService.getRegistry().getDevices()) {
                registryListener.deviceAdded(device);
            }

            upnpService.getControlPoint().search();
        }

        public void onServiceDisconnected(ComponentName className) {
            upnpService = null;
        }
    };

    public class DlnaRegistryListener extends DefaultRegistryListener {
        @Override
        public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
            deviceAdded(device);
        }

        @Override
        public void remoteDeviceRemoved(Registry registry, RemoteDevice device) {
            deviceRemoved(device);
        }

        @Override
        public void localDeviceAdded(Registry registry, LocalDevice device) {
            deviceAdded(device);
        }

        @Override
        public void localDeviceRemoved(Registry registry, LocalDevice device) {
            deviceRemoved(device);
        }

        public void deviceAdded(final Device device) {
            DLNADevice dlnaDevice = new DLNADevice(device);
            int index = discoveredDevices.indexOf(dlnaDevice);
            if(index > -1) {
                discoveredDevices.set(index, dlnaDevice);
            } else {
                discoveredDevices.add(dlnaDevice);
            }
            listener.onDeviceDetected(dlnaDevice);
        }

        public void deviceRemoved(final Device device) {
            DLNADevice dlnaDevice = new DLNADevice(device);
            discoveredDevices.remove(dlnaDevice);
            listener.onDeviceRemoved(dlnaDevice);
        }
    }
}
