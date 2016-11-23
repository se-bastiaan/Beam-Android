/*
 * This file is part of Popcorn Time.
 *
 * Popcorn Time is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Popcorn Time is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Popcorn Time. If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.se_bastiaan.beam;

import android.content.Context;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.github.se_bastiaan.beam.airplay.AirPlayClient;
import com.github.se_bastiaan.beam.airplay.AirPlayDevice;
import com.github.se_bastiaan.beam.dlna.DLNAClient;
import com.github.se_bastiaan.beam.dlna.DLNADevice;
import com.github.se_bastiaan.beam.googlecast.GoogleCastClient;
import com.github.se_bastiaan.beam.googlecast.GoogleDevice;
import com.github.se_bastiaan.beam.model.Playback;

/**
 * BeamManager.java
 * <p/>
 * This class is the god over all casting clients, those are:
 * {@link AirPlayClient}, {@link DLNAClient}, {@link GoogleCastClient}
 * It takes note when a device has been detected or removed, controls when a device is connected and chooses which client should be used to cast for that specific {@link BeamDevice}
 */
public class BeamManager {

    private static BeamManager INSTANCE;

    private GoogleCastClient googleCastClient;
    private AirPlayClient airPlayClient;
    private DLNAClient dlnaClient;
    private List<BeamListener> listeners = null;
    private BeamDevice currentDevice;
    private Boolean connected = false;
    private Set<BeamDevice> discoveredDevices = new HashSet<>();

    private BeamManager(Context context) {
        listeners = new ArrayList<>();

        googleCastClient = new GoogleCastClient(context, internalListener);
        airPlayClient = new AirPlayClient(context, internalListener);
        dlnaClient = new DLNAClient(context, internalListener);
    }

    public static BeamManager getInstance(Context context) {
        context = context.getApplicationContext();

        if (INSTANCE == null) {
            INSTANCE = new BeamManager(context);
        } else {
            INSTANCE.setContext(context);
        }

        return INSTANCE;
    }

    public boolean addListener(BeamListener listener) {
        return listeners.add(listener);
    }

    public boolean removeListener(BeamListener listener) {
        return listeners.remove(listener);
    }

    private void setContext(Context context) {
        context = context.getApplicationContext();
        googleCastClient.setContext(context);
        airPlayClient.setContext(context);
        dlnaClient.setContext(context);
    }

    public void destroy() {
        airPlayClient.destroy();
        dlnaClient.destroy();
        googleCastClient.destroy();
    }

    public BeamDevice[] getDevices() {
        return discoveredDevices.toArray(new BeamDevice[discoveredDevices.size()]);
    }

    public boolean hasDevices() {
        return discoveredDevices.size() > 0;
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean stop() {
        if (!connected) return false;

        if (currentDevice instanceof GoogleDevice) {
            googleCastClient.stop();
        } else if (currentDevice instanceof AirPlayDevice) {
            airPlayClient.stop();
        } else if (currentDevice instanceof DLNADevice) {
            dlnaClient.stop();
        }

        return false;
    }

    public boolean loadMedia(Playback playback, String location) {
        return loadMedia(playback, location, false);
    }

    public boolean loadMedia(Playback playback, String location, Boolean subs) {
        if (!connected) return false;

        try {
            URL url = new URL(location);
            URI uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(), url.getQuery(), url.getRef());
            location = uri.toString();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        if (currentDevice instanceof GoogleDevice) {
            googleCastClient.loadMedia(playback, location);
        } else if (currentDevice instanceof AirPlayDevice) {
            airPlayClient.loadMedia(playback, location);
        } else if (currentDevice instanceof DLNADevice) {
            dlnaClient.loadMedia(playback, location);
        }

        return false;
    }

    public void setDevice(BeamDevice beamDevice) {
        if(beamDevice == currentDevice) return;

        airPlayClient.disconnect();
        dlnaClient.disconnect();
        googleCastClient.disconnect();

        currentDevice = beamDevice;

        if (beamDevice != null) {
            if (beamDevice instanceof GoogleDevice) {
                googleCastClient.connect(beamDevice);
            } else if (beamDevice instanceof AirPlayDevice) {
                airPlayClient.connect(beamDevice);
            } else if (beamDevice instanceof DLNADevice) {
                dlnaClient.connect(beamDevice);
            }
        }
    }

    private BeamListener internalListener = new BeamListener() {
        @Override
        public void onConnected(BeamDevice device) {
            if((!device.equals(currentDevice) && !connected) || connected) return;

            connected = true;
            for(BeamListener listener : listeners) {
                listener.onConnected(currentDevice);
            }
        }

        @Override
        public void onDisconnected() {
            if(!connected) return;

            connected = false;
            currentDevice = null;

            for(BeamListener listener : listeners) {
                listener.onDisconnected();
            }
        }

        @Override
        public void onCommandFailed(String command, String message) {
            for(BeamListener listener : listeners) {
                listener.onCommandFailed(command, message);
            }
        }

        @Override
        public void onConnectionFailed() {
            for(BeamListener listener : listeners) {
                listener.onConnectionFailed();
            }
        }

        @Override
        public void onDeviceDetected(BeamDevice device) {
            if(discoveredDevices.add(device)) {
                for(BeamListener listener : listeners) {
                    listener.onDeviceDetected(device);
                }
            }
        }

        @Override
        public void onDeviceSelected(BeamDevice device) {
            currentDevice = device;
            for(BeamListener listener : listeners) {
                listener.onDeviceSelected(device);
            }
        }

        @Override
        public void onDeviceRemoved(BeamDevice device) {
            if(discoveredDevices.remove(device)) {
                for (BeamListener listener : listeners) {
                    listener.onDeviceRemoved(device);
                }
            }
        }

        @Override
        public void onVolumeChanged(double value, boolean isMute) {
            for(BeamListener listener : listeners) {
                listener.onVolumeChanged(value, isMute);
            }
        }

        @Override
        public void onReady() {
            for(BeamListener listener : listeners) {
                listener.onReady();
            }
        }

        @Override
        public void onPlayBackChanged(boolean isPlaying, float position) {
            for(BeamListener listener : listeners) {
                listener.onPlayBackChanged(isPlaying, position);
            }
        }
    };

}
