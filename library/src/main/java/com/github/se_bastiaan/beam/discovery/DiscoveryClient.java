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

package com.github.se_bastiaan.beam.discovery;

public interface DiscoveryClient {
    int RESCAN_INTERVAL = 10000;
    int RESCAN_ATTEMPTS = 6;
    int TIMEOUT = RESCAN_INTERVAL * RESCAN_ATTEMPTS;

    /**
     * Starts the DiscoveryProvider.
     */
    void start();

    /**
     * Stops the DiscoveryProvider.
     */
    void stop();

    /**
     * Restarts the DiscoveryProvider.
     */
    void restart();

    /**
     * Sends out discovery query without a full restart
     */
    void rescan();

    /**
     * Resets the DiscoveryProvider.
     */
    void reset();

    /** Adds a DiscoveryProviderListener, which should be the DiscoveryManager */
    void addListener(DiscoveryClientListener listener);

    /** Removes a DiscoveryProviderListener. */
    void removeListener(DiscoveryClientListener listener);

}