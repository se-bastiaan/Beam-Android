package com.github.se_bastiaan.beam.control;

import com.github.se_bastiaan.beam.MediaData;
import com.github.se_bastiaan.beam.device.BeamDevice;

public interface ControlClient {

    long PLAYBACK_POLL_INTERVAL = 2000;

    boolean canHandleDevice(BeamDevice device);

    void loadMedia(MediaData mediaData);

    void connect(BeamDevice device);

    void disconnect();

    void play();

    void pause();

    void seek(long position);

    void stop();

    void setVolume(float volume);

    boolean canControlVolume();

    /** Adds a ControlClientListener, which should be the DiscoveryManager */
    void addListener(ControlClientListener listener);

    /** Removes a ControlClientListener. */
    void removeListener(ControlClientListener listener);

}
