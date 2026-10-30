package com.next.tv.launcher.widget;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks real-time active media playback on Android TV via MediaSessionManager
 * and NotificationListenerService. Pure system API, zero mock data.
 */
public final class MediaSessionTracker {
    private static final String TAG = "TvHome.MediaTracker";

    public static final class MediaPlaybackInfo {
        public final boolean isPlaying;
        public final String title;
        public final String artist;
        public final String appPackage;
        public final Bitmap albumArt;
        public final String albumArtUri;
        public final MediaController controller;

        public MediaPlaybackInfo(boolean isPlaying, String title, String artist, String appPackage,
                                 Bitmap albumArt, String albumArtUri) {
            this(isPlaying, title, artist, appPackage, albumArt, albumArtUri, null);
        }

        public MediaPlaybackInfo(boolean isPlaying, String title, String artist, String appPackage,
                                 Bitmap albumArt, String albumArtUri, MediaController controller) {
            this.isPlaying = isPlaying;
            this.title = title;
            this.artist = artist;
            this.appPackage = appPackage;
            this.albumArt = albumArt;
            this.albumArtUri = albumArtUri;
            this.controller = controller;
        }
    }

    private final Context context;
    private final AudioManager audioManager;
    private final MediaSessionManager sessionManager;
    private MediaController activeController;

    public MediaSessionTracker(Context context) {
        this.context = context.getApplicationContext();
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.sessionManager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
    }

    public MediaPlaybackInfo getPlaybackInfo() {
        boolean musicActive = (audioManager != null && audioManager.isMusicActive());
        ComponentName listenerComp = new ComponentName(context, MediaNotificationListener.class);

        List<MediaController> controllers = new ArrayList<>();
        if (sessionManager != null) {
            try {
                List<MediaController> list = sessionManager.getActiveSessions(listenerComp);
                if (list != null) controllers.addAll(list);
            } catch (Exception e) {
                try {
                    List<MediaController> list = sessionManager.getActiveSessions(null);
                    if (list != null) controllers.addAll(list);
                } catch (Exception ignored) {}
            }
        }

        // Check active notifications from NotificationListenerService if connected
        MediaNotificationListener notifListener = MediaNotificationListener.getInstance();
        if (notifListener != null) {
            try {
                StatusBarNotification[] sbns = notifListener.getActiveNotifications();
                if (sbns != null) {
                    for (StatusBarNotification sbn : sbns) {
                        Notification n = sbn.getNotification();
                        if (n != null && n.extras != null) {
                            MediaSession.Token token = n.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION);
                            if (token != null) {
                                boolean exists = false;
                                for (MediaController c : controllers) {
                                    if (c.getSessionToken().equals(token)) {
                                        exists = true;
                                        break;
                                    }
                                }
                                if (!exists) {
                                    controllers.add(new MediaController(context, token));
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        if (!controllers.isEmpty()) {
            for (MediaController mc : controllers) {
                PlaybackState state = mc.getPlaybackState();
                boolean isPlaying = (state != null && (state.getState() == PlaybackState.STATE_PLAYING || state.getState() == PlaybackState.STATE_BUFFERING));
                if (isPlaying || musicActive) {
                    String title = null;
                    String artist = null;
                    Bitmap art = null;
                    String artUri = null;

                    MediaMetadata metadata = mc.getMetadata();
                    if (metadata != null) {
                        title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
                        if (title == null || title.trim().isEmpty()) {
                            title = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
                        }
                        artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
                        if (artist == null || artist.trim().isEmpty()) {
                            artist = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
                        }
                        if (artist == null || artist.trim().isEmpty()) {
                            artist = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
                        }

                        art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                        if (art == null) {
                            art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
                        }
                        artUri = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
                        if (artUri == null) {
                            artUri = metadata.getString(MediaMetadata.METADATA_KEY_ART_URI);
                        }
                        if (art == null && metadata.getDescription() != null) {
                            art = metadata.getDescription().getIconBitmap();
                        }
                        if (artUri == null && metadata.getDescription() != null && metadata.getDescription().getIconUri() != null) {
                            artUri = metadata.getDescription().getIconUri().toString();
                        }
                        if ((title == null || title.trim().isEmpty()) && metadata.getDescription() != null && metadata.getDescription().getTitle() != null) {
                            title = metadata.getDescription().getTitle().toString();
                        }
                        if ((artist == null || artist.trim().isEmpty()) && metadata.getDescription() != null && metadata.getDescription().getSubtitle() != null) {
                            artist = metadata.getDescription().getSubtitle().toString();
                        }
                    }

                    String appPkg = mc.getPackageName();
                    String appLabel = null;
                    if (appPkg != null) {
                        try {
                            PackageManager pm = context.getPackageManager();
                            ApplicationInfo ai = pm.getApplicationInfo(appPkg, 0);
                            CharSequence label = pm.getApplicationLabel(ai);
                            if (label != null && label.length() > 0) {
                                appLabel = label.toString();
                            }
                            if (art == null) {
                                Drawable d = pm.getApplicationIcon(ai);
                                if (d instanceof BitmapDrawable) {
                                    art = ((BitmapDrawable) d).getBitmap();
                                }
                            }
                        } catch (Exception ignored) {}
                    }

                    if (title == null || title.trim().isEmpty()) {
                        title = (appLabel != null) ? appLabel : "Đang phát";
                    }
                    if (artist == null || artist.trim().isEmpty()) {
                        artist = (appLabel != null && !appLabel.equals(title)) ? appLabel : "Trình phát TV";
                    }

                    this.activeController = mc;
                    return new MediaPlaybackInfo(true, title, artist, appPkg, art, artUri, mc);
                }
            }
        }

        if (musicActive) {
            return new MediaPlaybackInfo(true, "Đang phát", "Âm thanh hệ thống", null, null, null, null);
        }
        this.activeController = null;
        return new MediaPlaybackInfo(false, null, null, null, null, null, null);
    }

    public void togglePlayPause() {
        if (activeController != null) {
            try {
                PlaybackState ps = activeController.getPlaybackState();
                if (ps != null && ps.getState() == PlaybackState.STATE_PLAYING) {
                    activeController.getTransportControls().pause();
                } else if (activeController.getTransportControls() != null) {
                    activeController.getTransportControls().play();
                }
            } catch (Exception e) {
                Log.w(TAG, "togglePlayPause failed: " + e.getMessage());
            }
        }
    }
}
