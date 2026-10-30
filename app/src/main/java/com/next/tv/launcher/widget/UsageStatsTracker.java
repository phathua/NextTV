package com.next.tv.launcher.widget;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks daily screen-on and application usage time via Android's UsageStatsManager.
 * Runs completely locally with zero network overhead.
 */
public final class UsageStatsTracker {
    private static final String TAG = "TvHome.Usage";

    public static final class AppUsageItem {
        public final String packageName;
        public final String appName;
        public final long foregroundMillis;
        public final float percentage;
        public final Drawable icon;

        public AppUsageItem(String packageName, String appName, long foregroundMillis, float percentage, Drawable icon) {
            this.packageName = packageName;
            this.appName = appName;
            this.foregroundMillis = foregroundMillis;
            this.percentage = percentage;
            this.icon = icon;
        }
    }

    public static final class DailyUsageInfo {
        public final boolean hasPermission;
        public final long totalForegroundMillis;
        public final String topAppName;
        public final long topAppMillis;
        public final List<AppUsageItem> topApps;

        public DailyUsageInfo(boolean hasPermission, long totalForegroundMillis, String topAppName, long topAppMillis, List<AppUsageItem> topApps) {
            this.hasPermission = hasPermission;
            this.totalForegroundMillis = totalForegroundMillis;
            this.topAppName = topAppName;
            this.topAppMillis = topAppMillis;
            this.topApps = (topApps != null) ? topApps : new ArrayList<>();
        }

        public String formatTotalHoursMinutes() {
            long totalMinutes = totalForegroundMillis / (1000 * 60);
            long hours = totalMinutes / 60;
            long minutes = totalMinutes % 60;
            if (hours > 0) {
                return hours + "h " + minutes + "m";
            }
            return minutes + "m";
        }
    }

    private final Context context;

    public UsageStatsTracker(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean hasUsageStatsPermission() {
        try {
            AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) return false;
            int mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.getPackageName()
            );
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    public DailyUsageInfo getTodayUsage() {
        if (!hasUsageStatsPermission()) {
            return new DailyUsageInfo(false, 0, null, 0, null);
        }

        UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) {
            return new DailyUsageInfo(false, 0, null, 0, null);
        }

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startOfDay = cal.getTimeInMillis();
        long now = System.currentTimeMillis();
        long maxDaySpan = Math.max(1000L, now - startOfDay);

        PackageManager pm = context.getPackageManager();
        String myPkg = context.getPackageName();
        Map<String, Long> foregroundDurations = new HashMap<>();

        // 1. Try UsageEvents delta state machine for precise daily tracking
        try {
            UsageEvents events = usm.queryEvents(startOfDay, now);
            if (events != null) {
                UsageEvents.Event event = new UsageEvents.Event();
                Map<String, Long> lastResumedMap = new HashMap<>();

                while (events.hasNextEvent()) {
                    events.getNextEvent(event);
                    String pkg = event.getPackageName();
                    if (pkg == null || pkg.equals(myPkg)) continue;

                    int type = event.getEventType();
                    long timeStamp = event.getTimeStamp();

                    if (type == UsageEvents.Event.ACTIVITY_RESUMED) {
                        lastResumedMap.put(pkg, Math.max(timeStamp, startOfDay));
                    } else if (type == UsageEvents.Event.ACTIVITY_PAUSED || type == UsageEvents.Event.ACTIVITY_STOPPED) {
                        Long resumeTime = lastResumedMap.remove(pkg);
                        if (resumeTime != null && timeStamp > resumeTime) {
                            long delta = timeStamp - resumeTime;
                            long current = foregroundDurations.containsKey(pkg) ? foregroundDurations.get(pkg) : 0L;
                            foregroundDurations.put(pkg, current + delta);
                        }
                    }
                }

                // Handle currently running app
                for (Map.Entry<String, Long> entry : lastResumedMap.entrySet()) {
                    long delta = now - entry.getValue();
                    if (delta > 0 && delta <= maxDaySpan) {
                        long current = foregroundDurations.containsKey(entry.getKey()) ? foregroundDurations.get(entry.getKey()) : 0L;
                        foregroundDurations.put(entry.getKey(), current + delta);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "UsageEvents query failed: " + e.getMessage());
        }

        // 2. Fallback to queryUsageStats if UsageEvents returned empty
        if (foregroundDurations.isEmpty()) {
            List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now);
            if (stats != null) {
                for (UsageStats u : stats) {
                    if (u.getPackageName().equals(myPkg)) continue;
                    long time = u.getTotalTimeInForeground();
                    if (time > 0) {
                        foregroundDurations.put(u.getPackageName(), Math.min(time, maxDaySpan));
                    }
                }
            }
        }

        if (foregroundDurations.isEmpty()) {
            return new DailyUsageInfo(true, 0, null, 0, null);
        }

        // 3. Filter out system packages, launcher, and apps with < 60s usage
        List<Map.Entry<String, Long>> sortedApps = new ArrayList<>();
        long totalTime = 0;

        for (Map.Entry<String, Long> entry : foregroundDurations.entrySet()) {
            String pkg = entry.getKey();
            long time = Math.min(entry.getValue(), maxDaySpan);

            // Filter out transient usage under 60 seconds (prevents "Cài đặt (0m)")
            if (time < 60 * 1000L) continue;

            // Only track user-facing apps that have a launch intent (home screen launchable apps)
            if (!isLaunchableUserApp(pm, pkg)) continue;

            sortedApps.add(new java.util.AbstractMap.SimpleEntry<>(pkg, time));
            totalTime += time;
        }

        // Cap totalTime to strictly not exceed time since midnight
        totalTime = Math.min(totalTime, maxDaySpan);

        Collections.sort(sortedApps, (a, b) -> Long.compare(b.getValue(), a.getValue()));

        List<AppUsageItem> topApps = new ArrayList<>();
        String topAppName = null;
        long topAppMillis = 0;

        int limit = Math.min(3, sortedApps.size());
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Long> entry = sortedApps.get(i);
            String pkg = entry.getKey();
            long time = entry.getValue();
            String name = pkg;
            Drawable icon = null;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                name = pm.getApplicationLabel(ai).toString();
                // Fix common Android TV YouTube app package mislabeling
                if ("com.google.android.youtube.tv".equals(pkg) || "Android TV".equalsIgnoreCase(name)) {
                    if ("com.google.android.youtube.tv".equals(pkg)) {
                        name = "YouTube";
                    }
                }
                icon = pm.getApplicationIcon(ai);
            } catch (Exception ignored) {}

            float pct = totalTime > 0 ? (float) time / (float) totalTime : 0f;
            topApps.add(new AppUsageItem(pkg, name, time, pct, icon));
            if (i == 0) {
                topAppName = name;
                topAppMillis = time;
            }
        }

        return new DailyUsageInfo(true, totalTime, topAppName, topAppMillis, topApps);
    }

    private boolean isLaunchableUserApp(PackageManager pm, String pkg) {
        if (pkg == null) return false;
        // Strictly filter Android system framework, SystemUI, and core settings
        if (pkg.equals("android")
                || pkg.startsWith("com.android.systemui")
                || pkg.startsWith("com.google.android.tv.frameworkpackagestubs")
                || pkg.startsWith("com.google.android.katniss")
                || pkg.startsWith("com.google.android.inputmethod")
                || pkg.startsWith("com.android.inputmethod")
                || pkg.equals(context.getPackageName())) {
            return false;
        }

        try {
            // Must have a valid Leanback or regular Launcher Intent
            Intent lbIntent = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (lbIntent != null) return true;

            Intent stdIntent = pm.getLaunchIntentForPackage(pkg);
            return stdIntent != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static Intent getPermissionIntent() {
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }
}
