package com.github.se_bastiaan.beam;

import com.github.se_bastiaan.beam.device.BeamDevice;

public interface BeamControlListener {

    void onConnected(BeamManager manager, BeamDevice device);

    void onDisconnected(BeamManager manager);

    void onVolumeChanged(BeamManager manager, double value, boolean isMute);

    void onPlayBackChanged(BeamManager manager, boolean isPlaying, long position, long duration);

}
