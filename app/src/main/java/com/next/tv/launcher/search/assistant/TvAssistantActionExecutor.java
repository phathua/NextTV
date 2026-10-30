package com.next.tv.launcher.search.assistant;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.next.tv.launcher.lock.AppLockManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * Executes system-level controls (Volume, Bluetooth, App Launching) safely on Android TV.
 * Enforces strict AppLock & KidMode security gating and Levenshtein fuzzy matching.
 */
public final class TvAssistantActionExecutor {
    private static final String TAG = "TvHome.ActionExec";

    public static final class SystemActionResult {
        public final boolean success;
        public final String feedbackMessage;

        public SystemActionResult(boolean success, String feedbackMessage) {
            this.success = success;
            this.feedbackMessage = feedbackMessage;
        }
    }

    private static final class CachedAppEntry {
        final String packageName;
        final String label;
        final String normLabel;

        CachedAppEntry(String packageName, String label, String normLabel) {
            this.packageName = packageName;
            this.label = label;
            this.normLabel = normLabel;
        }
    }

    private static final Map<String, String[]> APP_ALIASES = new HashMap<>();
    static {
        APP_ALIASES.put("youtube", new String[]{"com.google.android.youtube.tv", "com.liskovsoft.videomanager", "com.teamsmart.videomanager.tv", "com.liskovsoft.smarttubetv.beta"});
        APP_ALIASES.put("netflix", new String[]{"com.netflix.ninja", "com.netflix.mediaclient"});
        APP_ALIASES.put("vtv", new String[]{"vn.vtv.vtvgo", "com.vtvgo"});
        APP_ALIASES.put("fpt", new String[]{"com.fptplay.activity", "com.fpt.play"});
        APP_ALIASES.put("k+", new String[]{"com.canal.kplus"});
        APP_ALIASES.put("stremio", new String[]{"com.stremio.one"});
        APP_ALIASES.put("nuvio", new String[]{"com.nuvio.tv"});
        APP_ALIASES.put("cloudstream", new String[]{"com.lagradost.cloudstream3"});
        APP_ALIASES.put("ch play", new String[]{"com.android.vending"});
        APP_ALIASES.put("google play", new String[]{"com.android.vending"});
        APP_ALIASES.put("cua hang", new String[]{"com.android.vending"});
        APP_ALIASES.put("cai dat", new String[]{"com.android.tv.settings", "com.android.settings"});
        APP_ALIASES.put("thiet lap", new String[]{"com.android.tv.settings", "com.android.settings"});
        APP_ALIASES.put("trinh duyet", new String[]{"org.mozilla.tv.firefox", "com.android.chrome"});
    }

    private final Context context;
    private final AudioManager audioManager;
    private final List<CachedAppEntry> appCache = new CopyOnWriteArrayList<>();

    public TvAssistantActionExecutor(Context context) {
        this.context = context.getApplicationContext();
        this.audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
        preloadAppCache();
    }

    /**
     * Preloads installed TV and mobile applications into a lightweight memory cache
     * on a background thread to prevent UI thread frame drops.
     */
    private void preloadAppCache() {
        Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "NextTV-AppPreload");
            t.setDaemon(true);
            return t;
        }).execute(() -> {
            try {
                PackageManager pm = context.getPackageManager();
                List<CachedAppEntry> entries = new ArrayList<>();

                Intent leanbackIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
                List<ResolveInfo> activities = pm.queryIntentActivities(leanbackIntent, 0);

                Intent standardIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> standardActivities = pm.queryIntentActivities(standardIntent, 0);
                activities.addAll(standardActivities);

                Map<String, Boolean> seen = new HashMap<>();
                for (ResolveInfo info : activities) {
                    if (info.activityInfo == null) continue;
                    String pkg = info.activityInfo.packageName;
                    if (seen.containsKey(pkg)) continue;
                    seen.put(pkg, Boolean.TRUE);

                    CharSequence labelSeq = info.loadLabel(pm);
                    String label = labelSeq != null ? labelSeq.toString() : pkg;
                    String norm = TvAssistantDispatcher.stripDiacritics(label);
                    entries.add(new CachedAppEntry(pkg, label, norm));
                }

                appCache.clear();
                appCache.addAll(entries);
            } catch (Exception e) {
                Log.w(TAG, "preloadAppCache failed: " + e.getMessage());
            }
        });
    }

    /**
     * Computes the Levenshtein distance between two strings for typo-tolerant fuzzy matching.
     */
    public static int levenshteinDistance(String a, String b) {
        if (a == null || b == null) return 999;
        int lenA = a.length();
        int lenB = b.length();
        if (lenA == 0) return lenB;
        if (lenB == 0) return lenA;

        int[] costs = new int[lenB + 1];
        for (int j = 0; j <= lenB; j++) costs[j] = j;

        for (int i = 1; i <= lenA; i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= lenB; j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]),
                        a.charAt(i - 1) == b.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[lenB];
    }

    /**
     * Executes volume adjustments via AudioManager.
     */
    public SystemActionResult executeVolume(int action, int percent) {
        if (audioManager == null) {
            return new SystemActionResult(false, "Không thể điều khiển âm thanh");
        }

        try {
            int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

            switch (action) {
                case TvAssistantDispatcher.VOL_ACTION_UP:
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                    return new SystemActionResult(true, "Đã tăng âm lượng");

                case TvAssistantDispatcher.VOL_ACTION_DOWN:
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                    return new SystemActionResult(true, "Đã giảm âm lượng");

                case TvAssistantDispatcher.VOL_ACTION_MUTE:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI);
                    } else {
                        audioManager.setStreamMute(AudioManager.STREAM_MUSIC, true);
                    }
                    return new SystemActionResult(true, "Đã tắt tiếng");

                case TvAssistantDispatcher.VOL_ACTION_UNMUTE:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI);
                    } else {
                        audioManager.setStreamMute(AudioManager.STREAM_MUSIC, false);
                    }
                    return new SystemActionResult(true, "Đã bật tiếng");

                case TvAssistantDispatcher.VOL_ACTION_SET:
                    if (percent >= 0 && maxVol > 0) {
                        int target = Math.round((percent / 100.0f) * maxVol);
                        target = Math.max(0, Math.min(maxVol, target));
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI);
                        return new SystemActionResult(true, "Âm lượng " + percent + "%");
                    }
                    break;
            }
        } catch (Exception e) {
            Log.w(TAG, "executeVolume error: " + e.getMessage());
        }

        return new SystemActionResult(false, "Không thể thay đổi âm lượng");
    }

    /**
     * Executes Bluetooth actions. If direct toggle is restricted by OS, opens Settings.
     */
    public SystemActionResult executeBluetooth(int action) {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (action == TvAssistantDispatcher.BT_ACTION_ENABLE) {
                if (adapter != null && !adapter.isEnabled()) {
                    try {
                        adapter.enable();
                        return new SystemActionResult(true, "Đã bật Bluetooth");
                    } catch (SecurityException se) {
                        openBluetoothSettings();
                        return new SystemActionResult(true, "Đang mở Cài đặt Bluetooth");
                    }
                }
                return new SystemActionResult(true, "Bluetooth đang bật");
            } else if (action == TvAssistantDispatcher.BT_ACTION_DISABLE) {
                if (adapter != null && adapter.isEnabled()) {
                    try {
                        adapter.disable();
                        return new SystemActionResult(true, "Đã tắt Bluetooth");
                    } catch (SecurityException se) {
                        openBluetoothSettings();
                        return new SystemActionResult(true, "Đang mở Cài đặt Bluetooth");
                    }
                }
                return new SystemActionResult(true, "Bluetooth đang tắt");
            } else {
                openBluetoothSettings();
                return new SystemActionResult(true, "Đang mở Cài đặt Bluetooth");
            }
        } catch (Exception e) {
            openBluetoothSettings();
            return new SystemActionResult(true, "Đang mở Cài đặt Bluetooth");
        }
    }

    private void openBluetoothSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(Settings.ACTION_SETTINGS);
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Resolves app from query and launches it with AppLock & KidMode security verification.
     */
    public SystemActionResult executeLaunchApp(String appQuery) {
        if (appQuery == null || appQuery.trim().isEmpty()) {
            return new SystemActionResult(false, "Chưa rõ tên ứng dụng");
        }

        String normQuery = TvAssistantDispatcher.stripDiacritics(appQuery);
        PackageManager pm = context.getPackageManager();
        AppLockManager lockManager = AppLockManager.getInstance(context);
        boolean isKid = lockManager.isKidModeActive();

        // 1. Check alias dictionary
        for (Map.Entry<String, String[]> entry : APP_ALIASES.entrySet()) {
            if (normQuery.contains(entry.getKey())) {
                for (String pkg : entry.getValue()) {
                    if (isPackageBlocked(lockManager, isKid, pkg)) {
                        return new SystemActionResult(false, "Ứng dụng này đã bị khóa");
                    }

                    Intent launchIntent = pm.getLaunchIntentForPackage(pkg);
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(launchIntent);
                        String label = getAppLabel(pm, pkg);
                        return new SystemActionResult(true, "Đang mở " + (label != null ? label : entry.getKey()));
                    }
                }
            }
        }

        // 2. Search cached entries using Levenshtein distance & closest length match
        CachedAppEntry bestMatch = null;
        int lowestCost = Integer.MAX_VALUE;

        for (CachedAppEntry entry : appCache) {
            if (entry.normLabel.equals(normQuery)) {
                bestMatch = entry;
                lowestCost = 0;
                break;
            }

            int lengthDelta = Math.abs(entry.normLabel.length() - normQuery.length());
            int dist = levenshteinDistance(entry.normLabel, normQuery);
            int cost = dist * 2 + lengthDelta;

            if (entry.normLabel.contains(normQuery) || normQuery.contains(entry.normLabel)) {
                cost = lengthDelta; // Heavily favor shorter substring matches
            }

            if (cost < lowestCost) {
                lowestCost = cost;
                bestMatch = entry;
            }
        }

        if (bestMatch != null && lowestCost <= 6) {
            String pkg = bestMatch.packageName;
            if (isPackageBlocked(lockManager, isKid, pkg)) {
                return new SystemActionResult(false, "Ứng dụng này đã bị khóa");
            }

            Intent intent = pm.getLaunchIntentForPackage(pkg);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return new SystemActionResult(true, "Đang mở " + bestMatch.label);
            }
        }

        return new SystemActionResult(false, "Không tìm thấy ứng dụng \"" + appQuery + "\"");
    }

    private boolean isPackageBlocked(AppLockManager lockManager, boolean isKid, String pkg) {
        if (pkg == null) return false;
        if (isKid && lockManager.isRestrictedInKidMode(pkg)) return true;
        return lockManager.isAppLocked(pkg) || lockManager.isVaultHidden(pkg);
    }

    private String getAppLabel(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return null;
        }
    }
}
