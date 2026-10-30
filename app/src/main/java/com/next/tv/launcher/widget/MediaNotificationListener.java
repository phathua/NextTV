package com.next.tv.launcher.widget;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class MediaNotificationListener extends NotificationListenerService {
    private static final String TAG = "TvHome.MediaNotif";
    private static MediaNotificationListener instance;
    private static Runnable onMediaChangedCallback;

    public static MediaNotificationListener getInstance() {
        return instance;
    }

    public static void setOnMediaChangedCallback(Runnable callback) {
        onMediaChangedCallback = callback;
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        instance = this;
        Log.i(TAG, "Notification listener connected successfully");
        notifyChange();
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        if (instance == this) instance = null;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        notifyChange();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        notifyChange();
    }

    private void notifyChange() {
        if (onMediaChangedCallback != null) {
            onMediaChangedCallback.run();
        }
    }
}
