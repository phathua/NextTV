package com.next.tv.launcher.controller;

import android.os.Handler;
import android.os.HandlerThread;

/**
 * Shared single-thread background worker executor.
 * Used for off-thread IO, TV recommendations, and widget updates (Weather, News, YouTube, Football)
 * without spinning up multiple long-running HandlerThreads.
 */
public final class BackgroundTaskExecutor {
    private final HandlerThread thread;
    private final Handler handler;

    public BackgroundTaskExecutor(String name) {
        thread = new HandlerThread(name);
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    public void post(Runnable runnable) {
        if (runnable != null) {
            handler.post(runnable);
        }
    }

    public void postDelayed(Runnable runnable, long delayMillis) {
        if (runnable != null) {
            handler.postDelayed(runnable, delayMillis);
        }
    }

    public void removeCallbacks(Runnable runnable) {
        if (runnable != null) {
            handler.removeCallbacks(runnable);
        }
    }

    public Handler getHandler() {
        return handler;
    }

    public void quit() {
        handler.removeCallbacksAndMessages(null);
        thread.quitSafely();
    }
}
