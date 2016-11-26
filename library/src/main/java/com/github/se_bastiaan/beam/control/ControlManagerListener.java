package com.github.se_bastiaan.beam.control;

import com.github.se_bastiaan.beam.device.BeamDevice;

public interface ControlManagerListener {

    void onConnected(ControlManager manager, BeamDevice device);

    void onDisconnected(ControlManager manage);

    void onVolumeChanged(ControlManager manager, double value, boolean isMute);

    void onPlayBackChanged(ControlManager manager, boolean isPlaying, long position, long duration);

}
