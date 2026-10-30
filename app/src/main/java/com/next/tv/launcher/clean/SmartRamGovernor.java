package com.next.tv.launcher.clean;

import android.app.ActivityManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import com.next.tv.launcher.widget.MediaSessionTracker;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Smart RAM Governor and Coordinator for Android TV.
 * Uses Real Local ADB Socket + dumpsys meminfo + am force-stop.
 * Zero mock, real kernel parameter management.
 */
public final class SmartRamGovernor {

    private static final String TAG = "TvHome.SmartRAM";

    private static final Set<String> SYSTEM_IMMUNE_PACKAGES = new HashSet<>();
    static {
        SYSTEM_IMMUNE_PACKAGES.add("android");
        SYSTEM_IMMUNE_PACKAGES.add("com.android.systemui");
        SYSTEM_IMMUNE_PACKAGES.add("com.android.tv.settings");
        SYSTEM_IMMUNE_PACKAGES.add("com.google.android.tv.settings");
        SYSTEM_IMMUNE_PACKAGES.add("com.google.android.inputmethod.latin");
        SYSTEM_IMMUNE_PACKAGES.add("com.android.inputmethod.latin");
        SYSTEM_IMMUNE_PACKAGES.add("com.liskovsoft.leankeyboard");
        SYSTEM_IMMUNE_PACKAGES.add("com.google.android.tts");
        SYSTEM_IMMUNE_PACKAGES.add("com.android.bluetooth");
        SYSTEM_IMMUNE_PACKAGES.add("com.google.android.tv.remote.service");
    }

    public static class CleanResult {
        public final int killedCount;
        public final long freedMb;
        public final int ramUsedPercentBefore;
        public final int ramUsedPercentAfter;
        public final List<String> killedPackages;
        public final List<String> protectedPackages;

        public CleanResult(int killedCount, long freedMb, int ramUsedPercentBefore, int ramUsedPercentAfter,
                           List<String> killedPackages, List<String> protectedPackages) {
            this.killedCount = killedCount;
            this.freedMb = freedMb;
            this.ramUsedPercentBefore = ramUsedPercentBefore;
            this.ramUsedPercentAfter = ramUsedPercentAfter;
            this.killedPackages = killedPackages;
            this.protectedPackages = protectedPackages;
        }
    }

    public interface CleanCallback {
        void onCleanCompleted(CleanResult result);
    }

    public static int getRamUsagePercent() {
        long totalKb = 0;
        long availKb = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("MemTotal:")) {
                    totalKb = parseMemKb(line);
                } else if (line.startsWith("MemAvailable:")) {
                    availKb = parseMemKb(line);
                }
                if (totalKb > 0 && availKb > 0) break;
            }
        } catch (Exception ignored) {}

        if (totalKb <= 0) return 65;
        long usedKb = totalKb - availKb;
        return (int) ((usedKb * 100) / totalKb);
    }

    private static long parseMemKb(String line) {
        try {
            String[] parts = line.split("\\s+");
            if (parts.length >= 2) {
                return Long.parseLong(parts[1]);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    public static boolean applyKernelLimits(Context context) {
        if (context == null) return false;
        SmartRamConfigManager config = SmartRamConfigManager.getInstance(context);
        int maxCached = config.getMaxCachedProcesses();
        int settleTime = 15000;
        if (SmartRamConfigManager.MODE_STRICT.equals(config.getMode())) {
            settleTime = 5000;
        } else if (SmartRamConfigManager.MODE_GENTLE.equals(config.getMode())) {
            settleTime = 30000;
        }

        String value = "max_cached_processes=" + maxCached + ",background_settle_time=" + settleTime;
        try {
            boolean ok = Settings.Global.putString(context.getContentResolver(), "activity_manager_constants", value);
            Log.d(TAG, "Applied kernel limits via Settings.Global: " + value + " -> result=" + ok);
            if (!ok) {
                LocalAdbBridge.getInstance(context).executeShell("settings put global activity_manager_constants " + value);
            }
            return ok;
        } catch (SecurityException e) {
            Log.w(TAG, "Missing WRITE_SECURE_SETTINGS, applying via Local ADB: " + e.getMessage());
            LocalAdbBridge.getInstance(context).executeShell("settings put global activity_manager_constants " + value);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply kernel limits", e);
            return false;
        }
    }

    /**
     * Executes smart RAM clean with Local ADB am force-stop and ActivityManager fallback.
     */
    public static CleanResult cleanRamSmartSync(Context context, boolean isManualTrigger, boolean forceAll) {
        if (context == null) {
            return new CleanResult(0, 0, 0, 0, Collections.emptyList(), Collections.emptyList());
        }
        Context appCtx = context.getApplicationContext();
        SmartRamConfigManager config = SmartRamConfigManager.getInstance(appCtx);
        ActivityManager am = (ActivityManager) appCtx.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) {
            return new CleanResult(0, 0, 0, 0, Collections.emptyList(), Collections.emptyList());
        }

        ActivityManager.MemoryInfo beforeMem = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(beforeMem);
        int ramPercentBefore = getRamUsagePercent();

        applyKernelLimits(appCtx);

        // Guard 1: Detect current Foreground top package
        String topPackage = getForegroundPackage(appCtx);

        // Guard 2: Media playback check
        String activeMediaPackage = null;
        if (config.isProtectActiveMedia()) {
            activeMediaPackage = getActiveMediaPackage(appCtx);
        }

        // Guard 3: UsageStats & Frequency Scoring
        long now = System.currentTimeMillis();
        long graceMillis = config.getRecentGraceMinutes() * 60 * 1000L;
        Set<String> hotApps = Collections.emptySet();
        Map<String, Long> lastUsedMap = Collections.emptyMap();
        if (config.isFrequencyAware()) {
            hotApps = getHotApps(appCtx, 3);
            lastUsedMap = getLastUsedTimes(appCtx);
        }

        // Guard 4: Whitelist packages
        Set<String> userWhitelist = config.getWhitelistPackages();

        boolean isEmergency = ramPercentBefore >= 88 || forceAll;
        boolean isOverloaded = ramPercentBefore >= config.getRamOverloadThresholdPercent() || isManualTrigger || isEmergency;

        // Resolve candidate packages using Real Local ADB PSS + UsageStats Fallback
        Set<String> candidatePackages = new HashSet<>();
        Map<String, Long> pssMap = new HashMap<>();

        LocalAdbBridge adbBridge = LocalAdbBridge.getInstance(appCtx);
        List<LocalAdbBridge.AppProcessMem> pssList = adbBridge.parseRunningProcessMem();
        if (!pssList.isEmpty()) {
            for (LocalAdbBridge.AppProcessMem pmMem : pssList) {
                if ("user".equals(pmMem.category) || "media".equals(pmMem.category)) {
                    candidatePackages.add(pmMem.packageName);
                    pssMap.put(pmMem.packageName, pmMem.pssKb);
                }
            }
        }

        // Fallback or augment with recent usage packages
        candidatePackages.addAll(lastUsedMap.keySet());

        PackageManager pm = appCtx.getPackageManager();
        List<ApplicationInfo> installedApps = pm.getInstalledApplications(0);
        for (ApplicationInfo ai : installedApps) {
            boolean isSystem = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
            if (!isSystem && pm.getLaunchIntentForPackage(ai.packageName) != null) {
                candidatePackages.add(ai.packageName);
            }
        }

        List<String> killedPackages = new ArrayList<>();
        List<String> protectedPackages = new ArrayList<>();
        String myPkg = appCtx.getPackageName();

        for (String pkg : candidatePackages) {
            if (pkg.equals(myPkg)) continue;

            // Core system immunity
            if (SYSTEM_IMMUNE_PACKAGES.contains(pkg)) {
                continue;
            }

            // User whitelist
            if (userWhitelist.contains(pkg)) {
                if (!protectedPackages.contains(pkg)) protectedPackages.add(pkg);
                continue;
            }

            // Guard 1: Top / Foreground app
            if (pkg.equals(topPackage)) {
                if (!protectedPackages.contains(pkg)) protectedPackages.add(pkg + " (Foreground)");
                continue;
            }

            // Guard 2: Playing media
            if (pkg.equals(activeMediaPackage)) {
                if (!protectedPackages.contains(pkg)) protectedPackages.add(pkg + " (Media)");
                continue;
            }

            // Check if system app
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                boolean isSystem = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                        && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
                if (isSystem) continue;
            } catch (Exception ignored) {
                // If package info is not found, check if it's a known daemon
                if (!pkg.contains("torrserver")) {
                    continue;
                }
            }

            // Guard 3: Grace period & Hot apps (Chỉ bảo vệ khi dọn tự động ngầm, người dùng bấm thủ công thì dọn dẹp thật)
            boolean shouldProtectRecent = !isManualTrigger && !isEmergency;
            if (shouldProtectRecent) {
                Long lastUsed = lastUsedMap.get(pkg);
                if (lastUsed != null && (now - lastUsed) < graceMillis) {
                    if (!protectedPackages.contains(pkg)) protectedPackages.add(pkg + " (Grace)");
                    continue;
                }

                if (hotApps.contains(pkg)) {
                    if (!protectedPackages.contains(pkg)) protectedPackages.add(pkg + " (Hot)");
                    continue;
                }

                if (!isOverloaded) {
                    if (lastUsed != null && (now - lastUsed) < (graceMillis * 2)) {
                        continue;
                    }
                }
            }

            // Execute Real Kill (Deep Kill via ADB am force-stop in Strict Mode or Manual)
            try {
                if (SmartRamConfigManager.MODE_STRICT.equals(config.getMode()) || isEmergency || isManualTrigger) {
                    adbBridge.deepKillApp(pkg);
                } else {
                    am.killBackgroundProcesses(pkg);
                }
                adbBridge.markPackageKilled(pkg);
                if (!killedPackages.contains(pkg)) {
                    killedPackages.add(pkg);
                }
            } catch (Exception ignored) {}
        }

        adbBridge.markAllKilled(killedPackages);

        System.gc();
        try {
            Thread.sleep(400);
        } catch (InterruptedException ignored) {}

        ActivityManager.MemoryInfo afterMem = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(afterMem);
        long memAvailDiffMb = (afterMem.availMem - beforeMem.availMem) / (1024 * 1024);
        long freedMb = Math.max(0L, memAvailDiffMb);

        int ramPercentAfter = getRamUsagePercent();
        Log.i(TAG, "SmartRAM cleaned: " + killedPackages.size() + " apps, freed ~" + freedMb + "MB (RAM: " + ramPercentBefore + "% -> " + ramPercentAfter + "%)");
        Log.i(TAG, "Killed: " + killedPackages);
        Log.i(TAG, "Protected: " + protectedPackages);

        return new CleanResult(killedPackages.size(), freedMb, ramPercentBefore, ramPercentAfter, killedPackages, protectedPackages);
    }

    public static void cleanRamSmartAsync(Context context, boolean isManualTrigger, boolean forceAll, CleanCallback callback) {
        new Thread(() -> {
            CleanResult res = cleanRamSmartSync(context, isManualTrigger, forceAll);
            if (callback != null) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onCleanCompleted(res));
            }
        }).start();
    }

    private static String getForegroundPackage(Context context) {
        try {
            UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return null;
            long end = System.currentTimeMillis();
            long begin = end - (1000 * 60 * 5);
            UsageEvents events = usm.queryEvents(begin, end);
            if (events == null) return null;

            String top = null;
            UsageEvents.Event event = new UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                    top = event.getPackageName();
                }
            }
            return top;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String getActiveMediaPackage(Context context) {
        try {
            MediaSessionTracker tracker = new MediaSessionTracker(context);
            MediaSessionTracker.MediaPlaybackInfo info = tracker.getPlaybackInfo();
            if (info != null && info.isPlaying) {
                return info.appPackage;
            }
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null && am.isMusicActive() && info != null) {
                return info.appPackage;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static Set<String> getHotApps(Context context, int topN) {
        Set<String> hot = new HashSet<>();
        try {
            UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return hot;
            long end = System.currentTimeMillis();
            long begin = end - (1000L * 60 * 60 * 24 * 7); // 7 days
            List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_WEEKLY, begin, end);
            if (stats == null || stats.isEmpty()) return hot;

            stats.sort((a, b) -> Long.compare(b.getTotalTimeInForeground(), a.getTotalTimeInForeground()));
            int count = 0;
            String myPkg = context.getPackageName();
            for (UsageStats s : stats) {
                String pkg = s.getPackageName();
                if (pkg.equals(myPkg) || SYSTEM_IMMUNE_PACKAGES.contains(pkg)) continue;
                hot.add(pkg);
                count++;
                if (count >= topN) break;
            }
        } catch (Exception ignored) {}
        return hot;
    }

    private static Map<String, Long> getLastUsedTimes(Context context) {
        try {
            UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return Collections.emptyMap();
            long end = System.currentTimeMillis();
            long begin = end - (1000L * 60 * 60 * 12); // Last 12 hours
            Map<String, UsageStats> map = usm.queryAndAggregateUsageStats(begin, end);
            if (map == null) return Collections.emptyMap();

            HashMap<String, Long> result = new HashMap<>();
            for (Map.Entry<String, UsageStats> entry : map.entrySet()) {
                result.put(entry.getKey(), entry.getValue().getLastTimeUsed());
            }
            return result;
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }
}
