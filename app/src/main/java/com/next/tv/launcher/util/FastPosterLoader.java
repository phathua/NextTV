package com.next.tv.launcher.util;

import com.next.tv.launcher.Config;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Lightweight, memory-efficient poster image loader for TV recommendation cards.
 * Uses strict RGB_565 decoding, downsamples to card dimensions, and bounds memory usage to ~6 MB.
 * Automatically identifies and caches whether an image is portrait (height > width * 1.15).
 */
public final class FastPosterLoader {
    private static final String TAG = "TvHome.PosterLoader";
    private static final int MAX_CACHE_SIZE_BYTES = 6 * 1024 * 1024; // 6 MB max
    private static final int TARGET_LANDSCAPE_W = 320;
    private static final int TARGET_LANDSCAPE_H = 180;
    private static final int TARGET_PORTRAIT_W = 220;
    private static final int TARGET_PORTRAIT_H = 330;

    public interface AspectRatioListener {
        void onAspectRatioDetected(String url, boolean isPortrait);
    }

    private static volatile FastPosterLoader instance;

    private final LruCache<String, Bitmap> memoryCache;
    private final Map<String, Boolean> portraitUrlCache = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static FastPosterLoader getInstance() {
        if (instance == null) {
            synchronized (FastPosterLoader.class) {
                if (instance == null) {
                    instance = new FastPosterLoader();
                }
            }
        }
        return instance;
    }

    private FastPosterLoader() {
        memoryCache = new LruCache<String, Bitmap>(MAX_CACHE_SIZE_BYTES) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount();
            }
        };

        executor = Executors.newFixedThreadPool(2, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "poster-loader");
                t.setPriority(Thread.MIN_PRIORITY);
                t.setDaemon(true);
                return t;
            }
        });
    }

    public Boolean isUrlPortrait(String url) {
        return portraitUrlCache.get(url);
    }

    public void load(Uri uri, ImageView target, int placeholderRes) {
        load(uri, target, placeholderRes, false, null);
    }

    public void load(Uri uri, ImageView target, int placeholderRes, boolean isPortrait, AspectRatioListener listener) {
        if (uri == null) {
            target.setTag(null);
            if (placeholderRes != 0) target.setImageResource(placeholderRes);
            else target.setImageDrawable(null);
            return;
        }

        String urlString = uri.toString();
        target.setTag(urlString);

        Bitmap cached = memoryCache.get(urlString);
        if (cached != null && !cached.isRecycled()) {
            target.setImageBitmap(cached);
            return;
        }

        if (placeholderRes != 0) {
            target.setImageResource(placeholderRes);
        }
        // If placeholderRes is 0, preserve whatever Bitmap or drawable target already has to avoid flickering!

        final Context appContext = target.getContext().getApplicationContext();
        executor.execute(() -> {
            Bitmap loaded = downloadAndDecode(appContext, urlString, isPortrait, listener);
            if (loaded != null) {
                memoryCache.put(urlString, loaded);
                mainHandler.post(() -> {
                    if (urlString.equals(target.getTag())) {
                        target.setImageBitmap(loaded);
                    }
                });
            }
        });
    }

    private Bitmap downloadAndDecode(Context context, String urlString, boolean isPortrait, AspectRatioListener listener) {
        HttpURLConnection conn = null;
        InputStream in = null;
        try {
            byte[] data = null;
            if (urlString.startsWith("assets://")) {
                if (context != null) {
                    in = context.getAssets().open(urlString.substring("assets://".length()));
                    data = readStream(in);
                }
            } else if (urlString.startsWith("file:///android_asset/")) {
                if (context != null) {
                    in = context.getAssets().open(urlString.substring("file:///android_asset/".length()));
                    data = readStream(in);
                }
            } else if (urlString.startsWith("file://")) {
                try {
                    java.io.File localFile = new java.io.File(Uri.parse(urlString).getPath());
                    if (localFile.exists()) {
                        in = new java.io.FileInputStream(localFile);
                        data = readStream(in);
                    }
                } catch (Exception ignored) {}
                if (data == null && context != null) {
                    try {
                        in = context.getContentResolver().openInputStream(Uri.parse(urlString));
                        data = readStream(in);
                    } catch (Exception ignored) {}
                }
            } else if (urlString.startsWith("android.resource://") || urlString.startsWith("content://")) {
                if (context != null) {
                    in = context.getContentResolver().openInputStream(Uri.parse(urlString));
                    data = readStream(in);
                }
            } else {
                URL url = new URL(urlString);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(6000);
                conn.setRequestProperty("User-Agent", com.next.tv.launcher.util.NetworkConstants.DEFAULT_USER_AGENT);
                conn.setInstanceFollowRedirects(true);
                conn.setDoInput(true);
                conn.connect();

                int code = conn.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    return null;
                }

                in = conn.getInputStream();
                data = readStream(in);
            }

            if (data == null || data.length == 0) return null;

            // First decode with inJustDecodeBounds to check dimensions
            BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
            boundsOptions.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, boundsOptions);

            int width = boundsOptions.outWidth;
            int height = boundsOptions.outHeight;

            boolean detectedPortrait = height > (int) (width * 1.15f);
            portraitUrlCache.put(urlString, detectedPortrait);

            if (listener != null) {
                mainHandler.post(() -> listener.onAspectRatioDetected(urlString, detectedPortrait));
            }

            int targetW = (isPortrait || detectedPortrait) ? TARGET_PORTRAIT_W : TARGET_LANDSCAPE_W;
            int targetH = (isPortrait || detectedPortrait) ? TARGET_PORTRAIT_H : TARGET_LANDSCAPE_H;

            // Compute sample size
            int sampleSize = 1;
            if (width > targetW || height > targetH) {
                int halfWidth = width / 2;
                int halfHeight = height / 2;
                while ((halfWidth / sampleSize) >= targetW && (halfHeight / sampleSize) >= targetH) {
                    sampleSize *= 2;
                }
            }

            // Decode actual bitmap with RGB_565 (saves 50% RAM compared to ARGB_8888)
            BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
            decodeOptions.inSampleSize = sampleSize;
            decodeOptions.inPreferredConfig = Bitmap.Config.RGB_565;
            decodeOptions.inDither = true;

            return BitmapFactory.decodeByteArray(data, 0, data.length, decodeOptions);
        } catch (Exception e) {
            Log.d(TAG, "Failed loading poster " + urlString + ": " + e.getMessage());
            return null;
        } finally {
            if (in != null) {
                try { in.close(); } catch (Exception ignored) {}
            }
            if (conn != null) {
                try { conn.disconnect(); } catch (Exception ignored) {}
            }
        }
    }

    private byte[] readStream(InputStream in) {
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) {
                out.write(buf, 0, r);
            }
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Completely clear all cached bitmaps to free RAM when launcher goes to background.
     */
    public void clear() {
        memoryCache.evictAll();
    }
}
