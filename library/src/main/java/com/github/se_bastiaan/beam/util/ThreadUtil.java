package com.github.se_bastiaan.beam.util;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class ThreadUtil {

    private static Handler handler = new Handler(Looper.getMainLooper());
    private static final int NUM_OF_THREADS = 20;

    private static Executor executor;

    static {
        createExecutor();
    }

    static void createExecutor() {
        executor = Executors.newFixedThreadPool(NUM_OF_THREADS, new ThreadFactory() {
            @Override
            public Thread newThread(@NonNull Runnable r) {
                return new Thread(r);
            }
        });
    }

    public static void runOnMainThread(Runnable runnable) {
        handler.post(runnable);
    }

    public static void runInBackground(Runnable runnable, boolean forceNewThread) {
        if (forceNewThread || isMain()) {
            executor.execute(runnable);
        } else {
            runnable.run();
        }

    }

    public static void runInBackground(Runnable runnable) {
        runInBackground(runnable, false);
    }

    public static Executor getExecutor() {
        return executor;
    }

    private static boolean isMain() {
        return Looper.myLooper() == Looper.getMainLooper();
    }

}
