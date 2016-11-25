package com.github.se_bastiaan.beam.control;

import android.content.Context;

import java.util.concurrent.CopyOnWriteArrayList;

public class ControlManager {

    private Context context;
    private CopyOnWriteArrayList<ControlManagerListener> controlListeners;

    public ControlManager(Context context) {
        this.context = context;

        this.controlListeners = new CopyOnWriteArrayList<>();
    }

    public void addListener(ControlManagerListener listener) {
        controlListeners.add(listener);
    }

    public void removeListener(ControlManagerListener listener) {
        controlListeners.remove(listener);
    }

}
