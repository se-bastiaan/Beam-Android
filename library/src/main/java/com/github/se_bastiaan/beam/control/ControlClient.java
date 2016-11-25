package com.github.se_bastiaan.beam.control;

import com.github.se_bastiaan.beam.device.BeamDevice;

public interface ControlClient {

    boolean canHandleDevice(Class<? extends BeamDevice> clazz);



}
