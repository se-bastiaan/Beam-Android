package com.github.se_bastiaan.beam.logger;

public class Logger {

    private static Logger instance = new Logger();

    private ILogger logger = new NoOpLogger();

    public static Logger getInstance() {
        return instance;
    }

    private Logger() {
    }

    public void setLogger(ILogger logger) {
        this.logger = logger;
    }

    public static void d(String tag, String message) {
        instance.logger.d(tag, message);
    }

    public static void i(String tag, String message) {
        instance.logger.i(tag, message);
    }

    public static void w(String tag, String message, Throwable throwable) {
        instance.logger.w(tag, message, throwable);
    }

    public static void e(String tag, String message, Throwable throwable) {
        instance.logger.e(tag, message, throwable);
    }

}
