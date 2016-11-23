package com.github.se_bastiaan.beam;

public interface BeamListener {
    void onConnected(BeamDevice device);

    void onDisconnected();

    void onCommandFailed(String command, String message);

    void onConnectionFailed();

    void onDeviceDetected(BeamDevice device);

    void onDeviceSelected(BeamDevice device);

    void onDeviceRemoved(BeamDevice device);

    void onVolumeChanged(double value, boolean isMute);

    void onReady();

    void onPlayBackChanged(boolean isPlaying, float position);
}
