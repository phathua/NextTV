package com.next.tv.launcher.security;

import android.content.Context;
import org.json.JSONObject;

/**
 * Level 3 Native NDK Security Bridge.
 * Binds directly to libvault-jni.so for compile-time encrypted secrets,
 * runtime APK signature verification, anti-tamper inspection, and in-memory HMAC signing.
 */
public final class NativeSecurityBridge {
    static {
        System.loadLibrary("vault-jni");
    }

    private NativeSecurityBridge() {}

    public static native String getProxyBaseUrl(Context context);
    public static native String generateRequestHeadersJson(Context context, String query, long timestamp);
    public static native String getTmdbApiKey(Context context);
    public static native String getTmdbReadAccessToken(Context context);
    public static native String getPexelsApiKey(Context context);
    public static native String getUnsplashApiKey(Context context);
    public static native String getPixabayApiKey(Context context);

    public static String getPexelsKeySafe(Context context) {
        try {
            String key = getPexelsApiKey(context);
            if (key != null && !key.isEmpty()) return key;
        } catch (Throwable ignored) {}
        return "";
    }

    public static String getUnsplashKeySafe(Context context) {
        try {
            String key = getUnsplashApiKey(context);
            if (key != null && !key.isEmpty()) return key;
        } catch (Throwable ignored) {}
        return "";
    }

    public static String getPixabayKeySafe(Context context) {
        try {
            String key = getPixabayApiKey(context);
            if (key != null && !key.isEmpty()) return key;
        } catch (Throwable ignored) {}
        return "";
    }

    public static String getTmdbKeySafe(Context context) {
        try {
            String key = getTmdbApiKey(context);
            if (key != null && !key.isEmpty()) return key;
        } catch (Throwable ignored) {}
        return "";
    }

    public static String getTmdbTokenSafe(Context context) {
        try {
            String tok = getTmdbReadAccessToken(context);
            if (tok != null && !tok.isEmpty()) return tok;
        } catch (Throwable ignored) {}
        return "";
    }

    public static final class RequestAuthHeaders {
        public final String timestamp;
        public final String apkSignature;
        public final String signature;

        public RequestAuthHeaders(String timestamp, String apkSignature, String signature) {
            this.timestamp = timestamp;
            this.apkSignature = apkSignature;
            this.signature = signature;
        }
    }

    public static RequestAuthHeaders signRequest(Context context, String query, long timestamp) {
        try {
            String json = generateRequestHeadersJson(context, query, timestamp);
            if (json != null && !json.isEmpty()) {
                JSONObject obj = new JSONObject(json);
                return new RequestAuthHeaders(
                    obj.optString("timestamp"),
                    obj.optString("apk_signature"),
                    obj.optString("signature")
                );
            }
        } catch (Exception ignored) {}
        return new RequestAuthHeaders(String.valueOf(timestamp), "", "");
    }
}
