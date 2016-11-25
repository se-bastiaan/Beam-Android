package com.github.se_bastiaan.beam.control.client;

import com.github.se_bastiaan.beam.control.ControlClient;
import com.github.se_bastiaan.beam.device.BeamDevice;
import com.github.se_bastiaan.beam.device.DLNADevice;

public class DLNAControlClient implements ControlClient {
    @Override
    public boolean canHandleDevice(Class<? extends BeamDevice> clazz) {
        return clazz == DLNADevice.class;
    }
}
