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

import com.github.druk.rxdnssd.BonjourService;

import java.net.InetAddress;
import java.util.Map;

/**
 * AirPlayDevice.java
 * <p/>
 * Wraps a {@link javax.jmdns.ServiceInfo} in a more general class that represents an Apple AirPlay device
 */
public class AirPlayDevice extends BeamDevice {

    public BonjourService service;

    private InetAddress ipAddress;
    private Integer port;
    private String srcvers;
    private String protovers;
    private Boolean pw = false;

    public AirPlayDevice(BonjourService service) {
        this.service = service;

        this.port = service.getPort();
        this.name = service.getServiceName();

        if (service.getInet4Address() != null) {
            ipAddress = service.getInet4Address();
        } else if (service.getInet6Address() != null) {
            ipAddress = service.getInet6Address();
        }

        Map<String, String> records = service.getTxtRecords();
        this.id = records.get("deviceid");
        this.model = records.get("model");
        this.srcvers = records.get("srcvers");
        this.protovers = records.containsKey("protovers") ? records.get("protovers") : null;

        if (records.containsKey("pw") || records.containsKey("pin")) {
            this.pw = true;
        }
    }

    public InetAddress getIpAddress() {
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

    public void setName(String name) {
        this.name = name;
    }

}
