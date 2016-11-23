package com.github.se_bastiaan.beam.logger;

public class NoOpLogger implements ILogger {

    @Override
    public void d(String message) {
        // Do nothing
    }

    @Override
    public void i(String message) {
        // Do nothing
    }

    @Override
    public void w(Throwable throwable, String message) {
        // Do nothing
    }

    @Override
    public void e(Throwable throwable, String message) {
        // Do nothing
    }

}
