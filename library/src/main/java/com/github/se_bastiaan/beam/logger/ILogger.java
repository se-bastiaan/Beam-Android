package com.github.se_bastiaan.beam.logger;

public interface ILogger {
    
    void d(String tag, String message);
    void i(String tag, String message);
    void w(String tag, String message, Throwable throwable);
    void e(String tag, String message, Throwable throwable);
    
}
