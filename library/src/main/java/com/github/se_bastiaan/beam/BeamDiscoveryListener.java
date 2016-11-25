package com.github.se_bastiaan.beam;

import com.github.se_bastiaan.beam.device.BeamDevice;

public interface BeamDiscoveryListener {

    void onDeviceAdded(BeamManager manager, BeamDevice device);

    void onDeviceRemoved(BeamManager manager, BeamDevice device);

    void onDeviceUpdated(BeamManager manager, BeamDevice device);

}
