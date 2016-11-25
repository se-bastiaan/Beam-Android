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

package com.github.se_bastiaan.beam;

import com.github.se_bastiaan.beam.device.BeamDevice;

public interface BeamListener {
    void onConnected(BeamDevice device);

    void onDisconnected();

    void onCommandFailed(String command, String message);

    void onConnectionFailed();

    void onDeviceDetected(BeamDevice device);

    void onDeviceSelected(BeamDevice device);

    void onDeviceRemoved(BeamDevice device);

    void onVolumeChanged(double value, boolean isMute);

    void onReady();

    void onPlayBackChanged(boolean isPlaying, float position);
}
