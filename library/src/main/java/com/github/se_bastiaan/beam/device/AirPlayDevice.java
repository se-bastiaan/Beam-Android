/*
 * Copyright (C) 2015-2016 Sébastiaan (github.com/se-bastiaan)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.se_bastiaan.beam.device;

import java.net.Inet4Address;

import javax.jmdns.ServiceInfo;

import com.github.se_bastiaan.beam.device.BeamDevice;

/**
 * AirPlayDevice.java
 * <p/>
 * Wraps a {@link javax.jmdns.ServiceInfo} in a more general class that represents an Apple AirPlay device
 */
public class AirPlayDevice extends BeamDevice {

    public ServiceInfo service;

    protected Inet4Address ipAddress;
    protected Integer port;
    private String protovers;
    private String srcvers;
    private Boolean pw = false;

    public AirPlayDevice(ServiceInfo service) {
        this.service = service;
        this.id = service.getPropertyString("deviceid");
        this.model = service.getPropertyString("model");
        this.protovers = service.getPropertyString("protovers");
        this.srcvers = service.getPropertyString("srcvers");
        Inet4Address[] inetAddresses = service.getInet4Addresses();
        if (inetAddresses.length > 0)
            this.ipAddress = inetAddresses[0];
        this.port = service.getPort();
        this.name = service.getName();

        byte[] pwBytes = service.getPropertyBytes("pw");
        byte[] pinBytes = service.getPropertyBytes("pin");
        if (pwBytes != null || pinBytes != null)
            this.pw = true;
    }

    public Inet4Address getIpAddress() {
        return ipAddress;
    }

    public Integer getPort() {
        return port;
    }

    public String getUrl() {
        return "http://" + ipAddress.getHostAddress() + ":" + port + "/";
    }

    public String getSourceVersion() {
        return srcvers;
    }

    public String getProtocolVersion() {
        return protovers;
    }

    public Boolean isPasswordProtected() {
        return pw;
    }

}
