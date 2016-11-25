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


import android.support.v7.media.MediaRouter.RouteInfo;

import com.google.android.gms.cast.CastDevice;

/**
 * GoogleCastDevice.java
 * <p/>
 * Wraps a {@link RouteInfo} in a more general class that represents a Google Cast Device
 */
public class GoogleCastDevice extends BeamDevice {

    public RouteInfo routeInfo;
    private CastDevice device;

    public GoogleCastDevice(RouteInfo routeInfo) {
        this.routeInfo = routeInfo;
        device = CastDevice.getFromBundle(routeInfo.getExtras());
        this.name = device.getFriendlyName();
        this.model = device.getModelName();
        this.id = device.getDeviceId();
    }

    public CastDevice getCastDevice() {
        return device;
    }

}
