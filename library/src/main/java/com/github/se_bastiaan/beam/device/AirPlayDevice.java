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

import android.net.nsd.NsdServiceInfo;
import android.os.Build;

import com.github.se_bastiaan.beam.discovery.nsd.RecordResolver;

import java.net.InetAddress;

/**
 * AirPlayDevice.java
 * <p/>
 * Wraps a {@link javax.jmdns.ServiceInfo} in a more general class that represents an Apple AirPlay device
 */
public class AirPlayDevice extends BeamDevice {

    private NsdServiceInfo service;

    protected InetAddress ipAddress;
    protected Integer port;
    private String protovers;
    private String srcvers;
    private Boolean pw = false;

    public AirPlayDevice(NsdServiceInfo service, RecordResolver.Result records) {
        this.service = service;
        this.name = service.getServiceName();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && records == null) {
            this.ipAddress = service.getHost();
            this.port = service.getPort();

            this.id = new String(service.getAttributes().get("deviceid"));
            this.model = new String(service.getAttributes().get("model"));
            if (service.getAttributes().containsKey("protovers")) {
                this.protovers = new String(service.getAttributes().get("protovers"));
            }
            if (service.getAttributes().containsKey("srcvers")) {
                this.srcvers = new String(service.getAttributes().get("srcvers"));
            }
            byte[] pwBytes = service.getAttributes().containsKey("pw") ? service.getAttributes().get("pw") : null;
            byte[] pinBytes = service.getAttributes().containsKey("pin") ? service.getAttributes().get("pin") : null;
            if (pwBytes != null || pinBytes != null) {
                this.pw = true;
            }
        }
    }

    public NsdServiceInfo getService() {
        return service;
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

}
