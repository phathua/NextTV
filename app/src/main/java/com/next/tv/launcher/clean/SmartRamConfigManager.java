package com.next.tv.launcher.clean;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages configuration and presets for Smart RAM Engine and Memory Cleaner Drawer.
 */
public final class SmartRamConfigManager {

    private static final String PREF_NAME = "smart_ram_prefs";

    public static final String MODE_GENTLE = "GENTLE";
    public static final String MODE_SMART_AUTO = "SMART_AUTO";
    public static final String MODE_STRICT = "STRICT";
    public static final String MODE_CUSTOM = "CUSTOM";

    private static final String KEY_MODE = "mode";
    private static final String KEY_MAX_CACHED = "max_cached_processes";
    private static final String KEY_CRON_INTERVAL = "cron_interval_minutes";
    private static final String KEY_RAM_OVERLOAD_THRESHOLD = "ram_overload_threshold_percent";
    private static final String KEY_RECENT_GRACE = "recent_grace_minutes";
    private static final String KEY_PROTECT_MEDIA = "protect_active_media";
    private static final String KEY_FREQUENCY_AWARE = "frequency_aware";
    private static final String KEY_WHITELIST = "whitelist_packages";

    private static volatile SmartRamConfigManager instance;
    private final SharedPreferences prefs;

    private SmartRamConfigManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static SmartRamConfigManager getInstance(Context context) {
        if (instance == null) {
            synchronized (SmartRamConfigManager.class) {
                if (instance == null) {
                    instance = new SmartRamConfigManager(context);
                }
            }
        }
        return instance;
    }

    public String getMode() {
        return prefs.getString(KEY_MODE, MODE_SMART_AUTO);
    }

    public void setMode(String mode) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_MODE, mode);
        if (MODE_GENTLE.equals(mode)) {
            editor.putInt(KEY_MAX_CACHED, 12);
            editor.putInt(KEY_RAM_OVERLOAD_THRESHOLD, 88);
            editor.putInt(KEY_RECENT_GRACE, 15);
        } else if (MODE_SMART_AUTO.equals(mode)) {
            editor.putInt(KEY_MAX_CACHED, 6);
            editor.putInt(KEY_RAM_OVERLOAD_THRESHOLD, 82);
            editor.putInt(KEY_RECENT_GRACE, 10);
            editor.putBoolean(KEY_PROTECT_MEDIA, true);
            editor.putBoolean(KEY_FREQUENCY_AWARE, true);
        } else if (MODE_STRICT.equals(mode)) {
            editor.putInt(KEY_MAX_CACHED, 2);
            editor.putInt(KEY_RAM_OVERLOAD_THRESHOLD, 75);
            editor.putInt(KEY_RECENT_GRACE, 1);
        }
        editor.apply();
    }

    public int getMaxCachedProcesses() {
        return prefs.getInt(KEY_MAX_CACHED, 6);
    }

    public void setMaxCachedProcesses(int maxCached) {
        prefs.edit().putInt(KEY_MAX_CACHED, maxCached).apply();
    }

    public int getCronIntervalMinutes() {
        return prefs.getInt(KEY_CRON_INTERVAL, 1); // 1 = When back to Home / periodic
    }

    public void setCronIntervalMinutes(int minutes) {
        prefs.edit().putInt(KEY_CRON_INTERVAL, minutes).apply();
    }

    public int getRamOverloadThresholdPercent() {
        return prefs.getInt(KEY_RAM_OVERLOAD_THRESHOLD, 82);
    }

    public void setRamOverloadThresholdPercent(int threshold) {
        prefs.edit().putInt(KEY_RAM_OVERLOAD_THRESHOLD, threshold).apply();
    }

    public int getRecentGraceMinutes() {
        return prefs.getInt(KEY_RECENT_GRACE, 10);
    }

    public void setRecentGraceMinutes(int minutes) {
        prefs.edit().putInt(KEY_RECENT_GRACE, minutes).apply();
    }

    public boolean isProtectActiveMedia() {
        return prefs.getBoolean(KEY_PROTECT_MEDIA, true);
    }

    public void setProtectActiveMedia(boolean protect) {
        prefs.edit().putBoolean(KEY_PROTECT_MEDIA, protect).apply();
    }

    public boolean isFrequencyAware() {
        return prefs.getBoolean(KEY_FREQUENCY_AWARE, true);
    }

    public void setFrequencyAware(boolean aware) {
        prefs.edit().putBoolean(KEY_FREQUENCY_AWARE, aware).apply();
    }

    public Set<String> getWhitelistPackages() {
        Set<String> set = prefs.getStringSet(KEY_WHITELIST, null);
        return set != null ? new HashSet<>(set) : new HashSet<>();
    }

    public void setWhitelistPackages(Set<String> packages) {
        prefs.edit().putStringSet(KEY_WHITELIST, packages != null ? new HashSet<>(packages) : Collections.emptySet()).apply();
    }

    public void toggleWhitelistPackage(String packageName) {
        Set<String> current = getWhitelistPackages();
        if (current.contains(packageName)) {
            current.remove(packageName);
        } else {
            current.add(packageName);
        }
        setWhitelistPackages(current);
    }
}
