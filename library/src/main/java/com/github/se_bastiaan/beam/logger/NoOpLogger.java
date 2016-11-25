package com.github.se_bastiaan.beam.logger;

public class NoOpLogger implements ILogger {

    @Override
    public void d(String tag, String message) {
        // Do nothing
    }

    @Override
    public void i(String tag, String message) {
        // Do nothing
    }

    @Override
    public void w(String tag, String message, Throwable throwable) {
        // Do nothing
    }

    @Override
    public void e(String tag, String message, Throwable throwable) {
        // Do nothing
    }

}
