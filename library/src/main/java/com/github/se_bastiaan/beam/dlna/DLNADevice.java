package com.github.se_bastiaan.beam.dlna;

import org.fourthline.cling.model.meta.Device;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.model.types.UDAServiceType;

import com.github.se_bastiaan.beam.BeamDevice;

/**
 * DLNADevice.java
 * <p/>
 * Wraps a {@link org.fourthline.cling.model.meta.Device} in a more general class that represents a DLNA Device
 */
public class DLNADevice extends BeamDevice {

    public static UDAServiceType AV_TRANSPORT = new UDAServiceType("AVTransport");
    public static UDAServiceType RENDERING_CONTROL = new UDAServiceType("RenderingControl");

    private Device device;

    public DLNADevice(Device device) {
        this.id = device.getIdentity().getUdn().getIdentifierString();
        this.name = device.getDetails().getFriendlyName();
        this.model = device.getDetails().getModelDetails().getModelName();
        this.device = device;
    }

    public Service getAVTransportService() {
        return device.findService(AV_TRANSPORT);
    }

    public Service getRenderingControlService() {
        return device.findService(RENDERING_CONTROL);
    }

}
