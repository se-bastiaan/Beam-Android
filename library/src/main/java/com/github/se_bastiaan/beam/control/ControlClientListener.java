package com.github.se_bastiaan.beam.control;

import com.github.se_bastiaan.beam.device.BeamDevice;

public interface ControlClientListener {

    void onConnected(ControlClient client, BeamDevice device);

    void onDisconnected(ControlClient client);

    void onVolumeChanged(ControlClient client, double value, boolean isMute);

    void onPlayBackChanged(ControlClient client, boolean isPlaying, long position, long duration);

}
