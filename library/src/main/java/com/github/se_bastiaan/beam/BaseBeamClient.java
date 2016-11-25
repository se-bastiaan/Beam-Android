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

public abstract class BaseBeamClient {

    public void loadMedia(Playback playback) {
        loadMedia(playback, 0);
    }

    public abstract void loadMedia(Playback playback, float position);

    public abstract void play();

    public abstract void pause();

    public abstract void seek(float position);

    public abstract void stop();

    public abstract void connect(BeamDevice device);

    public abstract void disconnect();

    public abstract void setVolume(float volume);

    public abstract boolean canControlVolume();

}
