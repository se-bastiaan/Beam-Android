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

package com.github.se_bastiaan.beam.discovery.ssdp;

public class Service {
    public static final String TAG = "service";
    public static final String TAG_SERVICE_TYPE = "serviceType";
    public static final String TAG_SERVICE_ID = "serviceId";
    public static final String TAG_SCPD_URL = "SCPDURL";
    public static final String TAG_CONTROL_URL = "controlURL";
    public static final String TAG_EVENTSUB_URL = "eventSubURL";

    public String baseURL;
    /* Required. UPnP service type. */
    public String serviceType;
    /* Required. Service identifier. */
    public String serviceId;
    /* Required. Relative URL for service description. */
    public String SCPDURL;
    /* Required. Relative URL for control. */
    public String controlURL;
    /* Relative. Relative URL for eventing. */
    public String eventSubURL;

}