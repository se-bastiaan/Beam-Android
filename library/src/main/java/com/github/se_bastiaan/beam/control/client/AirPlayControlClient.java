package com.github.se_bastiaan.beam.control.client;

import com.github.se_bastiaan.beam.control.ControlClient;
import com.github.se_bastiaan.beam.device.AirPlayDevice;
import com.github.se_bastiaan.beam.device.BeamDevice;

public class AirPlayControlClient implements ControlClient {

    @Override
    public boolean canHandleDevice(Class<? extends BeamDevice> clazz) {
        return clazz == AirPlayDevice.class;
    }

}
