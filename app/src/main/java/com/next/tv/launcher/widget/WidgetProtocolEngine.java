package com.next.tv.launcher.widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.next.tv.launcher.security.NativeSecurityBridge;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * NextTV Widget Protocol Engine (NWP-1).
 * Handles batch feed retrieval, Stale-While-Revalidate caching, SSRF prevention,
 * strict response size capping (256 KB), and redirect protection.
 */
public final class WidgetProtocolEngine {
    private static final String TAG = "TvHome.WidgetProtocol";
    private static final String PREFS_NAME = "widget_protocol_prefs";
    private static final String KEY_BATCH_CACHE = "batch_feed_json";
    private static final String KEY_BATCH_TIMESTAMP = "batch_feed_timestamp";

    private static final int MAX_RESPONSE_BYTES = 256 * 1024; // 256 KB cap
    private static final int TIMEOUT_MS = 7000;
    private static final int MAX_REDIRECT_HOPS = 3;
    private static final long CACHE_STALE_MS = 60 * 1000L; // 60 seconds stale check for live sports

    public interface BatchFeedCallback {
        void onBatchLoaded(JSONObject widgetsObj);
    }

    private static volatile WidgetProtocolEngine instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final ExecutorService executor;
    private final Handler mainHandler;

    public static WidgetProtocolEngine getInstance(Context context) {
        if (instance == null) {
            synchronized (WidgetProtocolEngine.class) {
                if (instance == null) {
                    instance = new WidgetProtocolEngine(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private WidgetProtocolEngine(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "NextTV-WidgetProtocol");
            t.setDaemon(true);
            return t;
        });
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Retrieves cached batch JSON data immediately (0 ms execution).
     */
    public JSONObject getCachedBatchJson() {
        String jsonStr = prefs.getString(KEY_BATCH_CACHE, null);
        if (jsonStr == null || jsonStr.isEmpty()) return null;
        try {
            JSONObject root = new JSONObject(jsonStr);
            return root.optJSONObject("widgets");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Checks if cached batch data is considered stale.
     */
    public boolean isCacheStale() {
        long lastTime = prefs.getLong(KEY_BATCH_TIMESTAMP, 0L);
        return (System.currentTimeMillis() - lastTime) > CACHE_STALE_MS;
    }

    /**
     * Fetches batch feed with Stale-While-Revalidate strategy.
     */
    public void fetchBatchAsync(String sportType, BatchFeedCallback callback) {
        // 1. Stale-While-Revalidate: deliver cache immediately if present
        JSONObject cached = getCachedBatchJson();
        if (cached != null && callback != null) {
            callback.onBatchLoaded(cached);
        }

        // 2. Fetch fresh batch in background
        executor.execute(() -> {
            JSONObject fresh = fetchBatchSync(sportType);
            if (fresh != null && callback != null) {
                mainHandler.post(() -> callback.onBatchLoaded(fresh));
            }
        });
    }

    /**
     * Synchronous network fetch with SSRF and redirect protection.
     * Must be called on a background thread.
     */
    public JSONObject fetchBatchSync(String sportType) {
        String baseUrl;
        try {
            baseUrl = NativeSecurityBridge.getProxyBaseUrl(context);
        } catch (Throwable ignored) {
            baseUrl = null;
        }
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "https://nexttv.vixlab.workers.dev";
        }

        String sport = (sportType != null && !sportType.isEmpty()) ? sportType : "soccer";
        String locParams = "";
        try {
            WeatherDataManager wm = new WeatherDataManager(context);
            if (wm.isCustomLocation()) {
                locParams = "&lat=" + wm.getSavedLatitude()
                        + "&lon=" + wm.getSavedLongitude()
                        + "&city=" + java.net.URLEncoder.encode(wm.getSavedCityName(), "UTF-8");
            }
        } catch (Throwable ignored) {}

        List<String> enabledIds = WidgetAddonManager.getInstance(context).getActiveEnabledAddonIds();
        String idsParam = "weather,sports,news,youtube";
        if (enabledIds != null && !enabledIds.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < enabledIds.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(enabledIds.get(i));
            }
            idsParam = sb.toString();
        }

        String endpointUrl = baseUrl + "/api/v1/widgets/batch?ids=" + idsParam + "&sport=" + sport + locParams;

        String rawJson = safeHttpGet(endpointUrl, 0);
        JSONObject widgets = null;
        if (rawJson != null && !rawJson.isEmpty()) {
            try {
                JSONObject root = new JSONObject(rawJson);
                widgets = root.optJSONObject("widgets");
            } catch (Exception e) {
                Log.e(TAG, "Failed parsing batch JSON response: " + e.getMessage());
            }
        }
        if (widgets == null) {
            widgets = getCachedBatchJson();
        }
        if (widgets == null) {
            widgets = new JSONObject();
        }

        // Fetch external third-party addons
        fetchExternalAddonsSync(widgets);

        // Cache the combined widgets
        try {
            JSONObject cacheRoot = new JSONObject();
            cacheRoot.put("widgets", widgets);
            prefs.edit()
                    .putString(KEY_BATCH_CACHE, cacheRoot.toString())
                    .putLong(KEY_BATCH_TIMESTAMP, System.currentTimeMillis())
                    .apply();
        } catch (Exception ignored) {}

        return widgets;
    }

    /**
     * Fetches real-time data from external third-party addons (type >= 100).
     * External endpoints are called without internal APK HMAC signing to prevent secret leakage.
     */
    private void fetchExternalAddonsSync(JSONObject widgets) {
        if (widgets == null) return;
        try {
            List<WidgetAddon> addons = WidgetAddonManager.getInstance(context).getInstalledAddons();
            for (WidgetAddon addon : addons) {
                if (!addon.isEnabled()) continue;
                int type = WidgetAddonManager.getWidgetTypeForId(addon.getId());
                if (type < 100) continue;

                String endpoint = addon.getEndpoint();
                if (endpoint == null || endpoint.trim().isEmpty()) continue;
                String targetUrl = endpoint.trim();

                // If endpoint is relative, resolve with manifest URL host
                if (targetUrl.startsWith("/") && addon.getManifestUrl() != null && !addon.getManifestUrl().isEmpty()) {
                    try {
                        URL baseManifest = new URL(addon.getManifestUrl());
                        targetUrl = new URL(baseManifest, targetUrl).toString();
                    } catch (Exception ignored) {}
                }

                // Fetch data without signing
                String res = safeHttpGet(targetUrl, 0, null);
                if (res != null && !res.trim().isEmpty()) {
                    try {
                        JSONObject customObj = new JSONObject(res);
                        widgets.put(addon.getId(), customObj);
                    } catch (Exception e) {
                        Log.w(TAG, "Invalid JSON from external addon " + addon.getId() + ": " + e.getMessage());
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Error fetching external addons: " + t.getMessage());
        }
    }

    /**
     * Safe HTTP GET with SSRF filtering, redirect validation, and 256 KB response cap.
     */
    public String safeHttpGet(String urlStr, int hopCount) {
        return safeHttpGet(urlStr, hopCount, "widgets_batch");
    }

    /**
     * Safe HTTP GET with SSRF filtering, redirect validation, configurable HMAC signing, and 256 KB response cap.
     */
    public String safeHttpGet(String urlStr, int hopCount, String signAction) {
        if (hopCount > MAX_REDIRECT_HOPS) {
            Log.w(TAG, "Exceeded maximum redirect hops: " + urlStr);
            return null;
        }

        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);

            // 1. Enforce HTTPS only
            if (!"https".equalsIgnoreCase(url.getProtocol())) {
                Log.w(TAG, "SSRF Blocked: Insecure protocol " + url.getProtocol());
                return null;
            }

            // 2. Resolve IP on background thread and block private/loopback/link-local ranges
            InetAddress addr = InetAddress.getByName(url.getHost());
            if (addr.isLoopbackAddress() || addr.isSiteLocalAddress()
                    || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()) {
                Log.w(TAG, "SSRF Blocked: Host resolves to internal IP: " + addr.getHostAddress());
                return null;
            }

            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setInstanceFollowRedirects(false); // Disable auto-redirect to inspect Location
            conn.setRequestProperty("User-Agent", "NextTV-Launcher/1.0");
            conn.setRequestProperty("Accept", "application/json");

            // Attach Release Keystore & HMAC-SHA256 signature headers if signAction is provided
            if (signAction != null && !signAction.isEmpty()) {
                long now = System.currentTimeMillis();
                NativeSecurityBridge.RequestAuthHeaders auth =
                        NativeSecurityBridge.signRequest(context, signAction, now);
                if (auth != null) {
                    conn.setRequestProperty("X-Client-Timestamp", auth.timestamp);
                    conn.setRequestProperty("X-Apk-Signature", auth.apkSignature);
                    conn.setRequestProperty("X-Client-Sign", auth.signature);
                }
            }

            int responseCode = conn.getResponseCode();

            // 3. Handle manual redirect validation
            if (responseCode == HttpURLConnection.HTTP_MOVED_PERM
                    || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                    || responseCode == HttpURLConnection.HTTP_SEE_OTHER
                    || responseCode == 307 || responseCode == 308) {
                String location = conn.getHeaderField("Location");
                if (location != null && !location.isEmpty()) {
                    if (location.startsWith("/")) {
                        location = url.getProtocol() + "://" + url.getHost() + location;
                    }
                    return safeHttpGet(location, hopCount + 1, signAction);
                }
                return null;
            }

            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP error " + responseCode + " for " + urlStr);
                return null;
            }

            // 4. Response Size Cap: Check Content-Length if available
            int contentLength = conn.getContentLength();
            if (contentLength > MAX_RESPONSE_BYTES) {
                Log.w(TAG, "Response size exceeded cap: " + contentLength + " bytes");
                return null;
            }

            // 5. Read stream with 256 KB hard limit
            try (InputStream is = conn.getInputStream();
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buf = new byte[4096];
                int totalRead = 0;
                int read;
                while ((read = is.read(buf)) != -1) {
                    totalRead += read;
                    if (totalRead > MAX_RESPONSE_BYTES) {
                        Log.w(TAG, "Response stream exceeded 256 KB cap during transfer");
                        return null;
                    }
                    baos.write(buf, 0, read);
                }
                return baos.toString(StandardCharsets.UTF_8.name());
            }

        } catch (Exception e) {
            Log.w(TAG, "Safe HTTP GET failed for " + urlStr + ": " + e.getMessage());
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
