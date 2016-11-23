package com.github.se_bastiaan.beam;

import com.github.se_bastiaan.beam.model.Playback;

public abstract class BaseBeamClient {

    public void loadMedia(Playback playback, String location) {
        loadMedia(playback, location, 0);
    }

    public abstract void loadMedia(Playback playback, String location, float position);

    public abstract void play();

    public abstract void pause();

    public abstract void seek(float position);

    public abstract void stop();

    public abstract void connect(BeamDevice device);

    public abstract void disconnect();

    public abstract void setVolume(float volume);

    public abstract boolean canControlVolume();

}
