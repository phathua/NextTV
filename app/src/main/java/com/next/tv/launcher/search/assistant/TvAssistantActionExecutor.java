package com.next.tv.launcher.search.assistant;

import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.next.tv.launcher.lock.AppLockManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Executes system-level controls (Volume, Bluetooth, App Launching) safely on Android TV.
 * Implements multi-tier app indexing (Exact, Alias, Token Set, Fuzzy Levenshtein)
 * with robust vendor-resilient intent dispatching.
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

    public static final class IndexedAppEntry {
        public final String packageName;
        public final String activityName;
        public final String label;
        public final String canonicalNorm;
        public final String strippedNorm;
        public final Set<String> tokens;
        public final Intent customIntent;
        public final boolean isSystemSettings;

        public IndexedAppEntry(String packageName, String activityName, String label,
                               String canonicalNorm, String strippedNorm,
                               Set<String> tokens, Intent customIntent, boolean isSystemSettings) {
            this.packageName = packageName;
            this.activityName = activityName;
            this.label = label;
            this.canonicalNorm = canonicalNorm;
            this.strippedNorm = strippedNorm;
            this.tokens = tokens;
            this.customIntent = customIntent;
            this.isSystemSettings = isSystemSettings;
        }
    }

    public static final class MatchResult {
        public final IndexedAppEntry app;
        public final double score;

        public MatchResult(IndexedAppEntry app, double score) {
            this.app = app;
            this.score = score;
        }
    }

    private static final Map<String, String[]> APP_ALIASES = new HashMap<>();
    static {
        APP_ALIASES.put("youtube", new String[]{"com.google.android.youtube.tv", "com.liskovsoft.videomanager", "com.teamsmart.videomanager.tv", "com.liskovsoft.smarttubetv.beta"});
        APP_ALIASES.put("yt", new String[]{"com.google.android.youtube.tv", "com.liskovsoft.videomanager", "com.teamsmart.videomanager.tv", "com.liskovsoft.smarttubetv.beta"});
        APP_ALIASES.put("smarttube", new String[]{"com.liskovsoft.videomanager", "com.teamsmart.videomanager.tv", "com.liskovsoft.smarttubetv.beta"});
        APP_ALIASES.put("smart tube", new String[]{"com.liskovsoft.videomanager", "com.teamsmart.videomanager.tv", "com.liskovsoft.smarttubetv.beta"});

        APP_ALIASES.put("netflix", new String[]{"com.netflix.ninja", "com.netflix.mediaclient"});

        APP_ALIASES.put("hbo", new String[]{"com.wbd.hbomax", "com.wbd.stream", "com.hbo.hbonow", "com.hbo.go"});
        APP_ALIASES.put("hbo max", new String[]{"com.wbd.hbomax", "com.wbd.stream", "com.hbo.hbonow", "com.hbo.go"});
        APP_ALIASES.put("max", new String[]{"com.wbd.stream", "com.wbd.hbomax", "com.hbo.hbonow"});
        APP_ALIASES.put("hbomax", new String[]{"com.wbd.hbomax", "com.wbd.stream", "com.hbo.hbonow", "com.hbo.go"});

        APP_ALIASES.put("vtv", new String[]{"vn.vtv.vtvgo", "com.vtvgo", "vn.vtvgo"});
        APP_ALIASES.put("vtv go", new String[]{"vn.vtv.vtvgo", "com.vtvgo", "vn.vtvgo"});
        APP_ALIASES.put("vtvgo", new String[]{"vn.vtv.vtvgo", "com.vtvgo", "vn.vtvgo"});

        APP_ALIASES.put("fpt", new String[]{"com.fptplay.activity", "com.fpt.play"});
        APP_ALIASES.put("fpt play", new String[]{"com.fptplay.activity", "com.fpt.play"});
        APP_ALIASES.put("fptplay", new String[]{"com.fptplay.activity", "com.fpt.play"});

        APP_ALIASES.put("vieon", new String[]{"com.vieon"});
        APP_ALIASES.put("vie on", new String[]{"com.vieon"});

        APP_ALIASES.put("tv360", new String[]{"com.viettel.tv360"});
        APP_ALIASES.put("viettel tv", new String[]{"com.viettel.tv360"});
        APP_ALIASES.put("viettel", new String[]{"com.viettel.tv360"});

        APP_ALIASES.put("k+", new String[]{"com.canal.kplus"});
        APP_ALIASES.put("kplus", new String[]{"com.canal.kplus"});
        APP_ALIASES.put("k plus", new String[]{"com.canal.kplus"});

        APP_ALIASES.put("ch play", new String[]{"com.android.vending"});
        APP_ALIASES.put("google play", new String[]{"com.android.vending"});
        APP_ALIASES.put("play store", new String[]{"com.android.vending"});
        APP_ALIASES.put("cua hang", new String[]{"com.android.vending"});
        APP_ALIASES.put("cua hang play", new String[]{"com.android.vending"});

        APP_ALIASES.put("stremio", new String[]{"com.stremio.one"});
        APP_ALIASES.put("nuvio", new String[]{"com.nuvio.tv"});
        APP_ALIASES.put("cloudstream", new String[]{"com.lagradost.cloudstream3"});
        APP_ALIASES.put("cloud stream", new String[]{"com.lagradost.cloudstream3"});

        APP_ALIASES.put("trinh duyet", new String[]{"org.mozilla.tv.firefox", "com.android.chrome"});
        APP_ALIASES.put("browser", new String[]{"org.mozilla.tv.firefox", "com.android.chrome"});
        APP_ALIASES.put("web", new String[]{"org.mozilla.tv.firefox", "com.android.chrome"});
        APP_ALIASES.put("chrome", new String[]{"com.android.chrome"});
        APP_ALIASES.put("firefox", new String[]{"org.mozilla.tv.firefox"});

        APP_ALIASES.put("xplore", new String[]{"com.lonelycatgames.Xplore"});
        APP_ALIASES.put("x-plore", new String[]{"com.lonelycatgames.Xplore"});
        APP_ALIASES.put("quan ly file", new String[]{"com.lonelycatgames.Xplore"});
        APP_ALIASES.put("file manager", new String[]{"com.lonelycatgames.Xplore"});

        APP_ALIASES.put("spotify", new String[]{"com.spotify.tv.android", "com.spotify.music"});
        APP_ALIASES.put("zing mp3", new String[]{"com.zing.mp3"});
        APP_ALIASES.put("zingmp3", new String[]{"com.zing.mp3"});
        APP_ALIASES.put("nhaccuatui", new String[]{"ht.nhaccuatui"});
        APP_ALIASES.put("mytv", new String[]{"com.vnpt.mytv.tv", "com.vnpt.mytv"});
        APP_ALIASES.put("my tv", new String[]{"com.vnpt.mytv.tv", "com.vnpt.mytv"});
        APP_ALIASES.put("iqiyi", new String[]{"com.iqiyi.i18n.tv", "com.iqiyi.i18n"});
        APP_ALIASES.put("wetv", new String[]{"com.wetv.tv", "com.tencent.qqlivei18n"});
        APP_ALIASES.put("we tv", new String[]{"com.wetv.tv", "com.tencent.qqlivei18n"});
        APP_ALIASES.put("disney", new String[]{"com.disney.disneyplus"});
        APP_ALIASES.put("apple tv", new String[]{"com.apple.atve.androidtv.appletv"});
    }

    private final Context context;
    private final AudioManager audioManager;
    private final List<IndexedAppEntry> appCache = new CopyOnWriteArrayList<>();

    public TvAssistantActionExecutor(Context context) {
        this.context = context.getApplicationContext();
        this.audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
        preloadAppCache();
    }

    public static String normalizeCanonical(String s) {
        if (s == null) return "";
        String norm = TvAssistantDispatcher.stripDiacritics(s);
        norm = norm.replaceAll("[^a-zA-Z0-9 ]", " ");
        norm = norm.replaceAll("\\s+", " ").trim();
        return norm.toLowerCase(Locale.ROOT);
    }

    public static String normalizeStripped(String s) {
        if (s == null) return "";
        String norm = TvAssistantDispatcher.stripDiacritics(s);
        norm = norm.replaceAll("[^a-zA-Z0-9]", "");
        return norm.toLowerCase(Locale.ROOT);
    }

    /**
     * Preloads shortcuts and installed applications into an indexed memory cache
     * on a background daemon thread to eliminate UI thread frame drops.
     */
    private void preloadAppCache() {
        Thread t = new Thread(() -> {
            try {
                List<IndexedAppEntry> entries = new ArrayList<>();

                // 1. Preload System Settings shortcuts using SystemSettings
                try {
                    Intent settingsIntent = com.next.tv.launcher.settings.SystemSettings.intent(context, Settings.ACTION_SETTINGS);
                    addSystemEntry(entries, "system.settings", "Cài đặt", "cai dat", settingsIntent);
                    addSystemEntry(entries, "system.settings", "Thiết lập", "thiet lap", settingsIntent);
                    addSystemEntry(entries, "system.settings", "Settings", "settings", settingsIntent);

                    Intent wifiIntent = com.next.tv.launcher.settings.SystemSettings.intent(context, Settings.ACTION_WIFI_SETTINGS);
                    addSystemEntry(entries, "system.wifi", "Cài đặt Wifi", "cai dat wifi", wifiIntent);
                    addSystemEntry(entries, "system.wifi", "Wifi", "wifi", wifiIntent);
                    addSystemEntry(entries, "system.wifi", "Mạng", "mang", wifiIntent);
                    addSystemEntry(entries, "system.wifi", "Internet", "internet", wifiIntent);

                    Intent btIntent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                    addSystemEntry(entries, "system.bluetooth", "Cài đặt Bluetooth", "cai dat bluetooth", btIntent);
                    addSystemEntry(entries, "system.bluetooth", "Bluetooth", "bluetooth", btIntent);

                    Intent appsIntent = new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
                    addSystemEntry(entries, "system.apps", "Quản lý ứng dụng", "quan ly ung dung", appsIntent);
                } catch (Throwable e) {
                    Log.w(TAG, "Failed loading system settings shortcuts: " + e.getMessage());
                }

                // 2. Preload installed Leanback & Standard applications
                PackageManager pm = context.getPackageManager();
                Intent leanbackIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
                List<ResolveInfo> activities = pm.queryIntentActivities(leanbackIntent, 0);

                Intent standardIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> standardActivities = pm.queryIntentActivities(standardIntent, 0);
                activities.addAll(standardActivities);

                Map<String, Boolean> seen = new HashMap<>();
                for (ResolveInfo info : activities) {
                    if (info.activityInfo == null) continue;
                    String pkg = info.activityInfo.packageName;
                    String act = info.activityInfo.name;
                    String key = pkg + "/" + act;
                    if (seen.containsKey(key)) continue;
                    seen.put(key, Boolean.TRUE);

                    CharSequence actLabelSeq = info.loadLabel(pm);
                    String actLabel = actLabelSeq != null ? actLabelSeq.toString() : "";
                    CharSequence appLabelSeq = null;
                    try {
                        appLabelSeq = (info.activityInfo.applicationInfo != null) ?
                                info.activityInfo.applicationInfo.loadLabel(pm) : null;
                    } catch (Exception ignored) {}
                    String appLabel = appLabelSeq != null ? appLabelSeq.toString() : "";

                    if (!actLabel.isEmpty()) {
                        indexAppEntry(entries, pkg, act, actLabel);
                    }
                    if (!appLabel.isEmpty() && !appLabel.equalsIgnoreCase(actLabel)) {
                        indexAppEntry(entries, pkg, act, appLabel);
                    }
                }

                synchronized (appCache) {
                    appCache.clear();
                    appCache.addAll(entries);
                }
            } catch (Exception e) {
                Log.w(TAG, "preloadAppCache failed: " + e.getMessage());
            }
        }, "NextTV-AppPreload");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        t.start();
    }

    private void addSystemEntry(List<IndexedAppEntry> list, String pkg, String label, String norm, Intent intent) {
        String canonical = normalizeCanonical(norm);
        String stripped = normalizeStripped(norm);
        Set<String> tokens = new HashSet<>(Arrays.asList(canonical.split(" ")));
        list.add(new IndexedAppEntry(pkg, null, label, canonical, stripped, tokens, intent, true));
    }

    private void indexAppEntry(List<IndexedAppEntry> list, String pkg, String act, String label) {
        String canonical = normalizeCanonical(label);
        String stripped = normalizeStripped(label);
        if (canonical.isEmpty()) return;
        Set<String> tokens = new HashSet<>(Arrays.asList(canonical.split(" ")));
        list.add(new IndexedAppEntry(pkg, act, label, canonical, stripped, tokens, null, false));
    }

    /**
     * Resolves an app query via multi-tier lookups:
     * Tier 1: Exact canonical & space-stripped matches
     * Tier 2: Alias dictionary mappings
     * Tier 3: Token set ratio & Substring containment
     * Tier 4: Fuzzy Levenshtein distance
     */
    public MatchResult resolveApp(String query) {
        if (query == null || query.trim().isEmpty()) return null;
        String canonicalQuery = normalizeCanonical(query);
        String strippedQuery = normalizeStripped(query);
        if (canonicalQuery.isEmpty()) return null;

        Set<String> queryTokens = new HashSet<>(Arrays.asList(canonicalQuery.split(" ")));

        // Tier 1: Exact canonical & space-stripped matches
        for (IndexedAppEntry entry : appCache) {
            if (canonicalQuery.equals(entry.canonicalNorm)) {
                return new MatchResult(entry, 100.0);
            }
            if (!strippedQuery.isEmpty() && strippedQuery.equals(entry.strippedNorm)) {
                return new MatchResult(entry, 98.0);
            }
        }

        // Tier 2: Alias dictionary match
        PackageManager pm = context.getPackageManager();
        for (Map.Entry<String, String[]> alias : APP_ALIASES.entrySet()) {
            String aliasKey = alias.getKey();
            String aliasStripped = aliasKey.replace(" ", "");
            if (canonicalQuery.equals(aliasKey) || strippedQuery.equals(aliasStripped) ||
                canonicalQuery.contains(aliasKey) || (strippedQuery.length() >= 4 && strippedQuery.contains(aliasStripped))) {
                for (String candidatePkg : alias.getValue()) {
                    for (IndexedAppEntry entry : appCache) {
                        if (candidatePkg.equals(entry.packageName)) {
                            return new MatchResult(entry, 95.0);
                        }
                    }
                    try {
                        pm.getPackageInfo(candidatePkg, 0);
                        Intent launch = pm.getLaunchIntentForPackage(candidatePkg);
                        if (launch != null) {
                            IndexedAppEntry synthesized = new IndexedAppEntry(
                                candidatePkg, null, getAppLabel(pm, candidatePkg),
                                normalizeCanonical(aliasKey), normalizeStripped(aliasKey),
                                Collections.emptySet(), launch, false
                            );
                            return new MatchResult(synthesized, 95.0);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        // Tier 3 & Tier 4: Token Set, Substring & Fuzzy Levenshtein
        MatchResult best = null;
        for (IndexedAppEntry entry : appCache) {
            double score = computeMatchScore(canonicalQuery, strippedQuery, queryTokens, entry);
            if (score >= 65.0) {
                if (best == null || score > best.score) {
                    best = new MatchResult(entry, score);
                }
            }
        }

        return best;
    }

    private double computeMatchScore(String queryCanon, String queryStripped, Set<String> queryTokens, IndexedAppEntry entry) {
        if (!queryTokens.isEmpty() && !entry.tokens.isEmpty()) {
            int commonTokens = 0;
            for (String t : queryTokens) {
                if (entry.tokens.contains(t)) commonTokens++;
            }
            if (commonTokens == queryTokens.size()) {
                double ratio = (double) queryTokens.size() / entry.tokens.size();
                return 80.0 + (ratio * 15.0);
            }
            if (commonTokens > 0) {
                double jaccard = (double) commonTokens / (queryTokens.size() + entry.tokens.size() - commonTokens);
                if (jaccard >= 0.5) {
                    return 70.0 + (jaccard * 15.0);
                }
            }
        }

        if (entry.canonicalNorm.contains(queryCanon) || queryCanon.contains(entry.canonicalNorm)) {
            double lenRatio = (double) Math.min(queryCanon.length(), entry.canonicalNorm.length())
                    / Math.max(queryCanon.length(), entry.canonicalNorm.length());
            return 75.0 + (lenRatio * 15.0);
        }
        if (!queryStripped.isEmpty() && (entry.strippedNorm.contains(queryStripped) || queryStripped.contains(entry.strippedNorm))) {
            double lenRatio = (double) Math.min(queryStripped.length(), entry.strippedNorm.length())
                    / Math.max(queryStripped.length(), entry.strippedNorm.length());
            return 72.0 + (lenRatio * 15.0);
        }

        int distCanon = levenshteinDistance(entry.canonicalNorm, queryCanon);
        int maxLenCanon = Math.max(entry.canonicalNorm.length(), queryCanon.length());
        double simCanon = maxLenCanon > 0 ? 1.0 - ((double) distCanon / maxLenCanon) : 0;

        int distStripped = levenshteinDistance(entry.strippedNorm, queryStripped);
        int maxLenStripped = Math.max(entry.strippedNorm.length(), queryStripped.length());
        double simStripped = maxLenStripped > 0 ? 1.0 - ((double) distStripped / maxLenStripped) : 0;

        double maxSim = Math.max(simCanon, simStripped);
        if (maxSim >= 0.70) {
            return maxSim * 85.0;
        }

        return 0.0;
    }

    public boolean hasAppMatch(String query) {
        if (query == null || query.trim().length() < 2) return false;
        MatchResult res = resolveApp(query);
        return res != null && res.score >= 70.0;
    }

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

    public SystemActionResult executeLaunchApp(String appQuery) {
        if (appQuery == null || appQuery.trim().isEmpty()) {
            return new SystemActionResult(false, "Chưa rõ tên ứng dụng");
        }

        MatchResult match = resolveApp(appQuery);
        if (match == null || match.app == null) {
            return new SystemActionResult(false, "Không tìm thấy ứng dụng \"" + appQuery + "\"");
        }

        IndexedAppEntry app = match.app;
        AppLockManager lockManager = AppLockManager.getInstance(context);
        boolean isKid = lockManager.isKidModeActive();

        if (isPackageBlocked(lockManager, isKid, app.packageName)) {
            return new SystemActionResult(false, "Ứng dụng này đã bị khóa");
        }

        // Custom intent (System Settings, Wifi, Bluetooth)
        if (app.customIntent != null) {
            try {
                Intent i = new Intent(app.customIntent);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
                return new SystemActionResult(true, "Đang mở " + app.label);
            } catch (Exception e) {
                Log.w(TAG, "Custom intent launch failed: " + e.getMessage());
            }
        }

        // Standard package launch with Leanback fallback and explicit ComponentName fallback
        PackageManager pm = context.getPackageManager();
        Intent intent = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            intent = pm.getLeanbackLaunchIntentForPackage(app.packageName);
        }
        if (intent == null) {
            intent = pm.getLaunchIntentForPackage(app.packageName);
        }
        if (intent == null && app.activityName != null) {
            intent = new Intent(Intent.ACTION_MAIN);
            intent.setComponent(new ComponentName(app.packageName, app.activityName));
        }

        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent);
                return new SystemActionResult(true, "Đang mở " + app.label);
            } catch (Exception e) {
                Log.w(TAG, "Activity launch failed: " + e.getMessage());
            }
        }

        return new SystemActionResult(false, "Không thể khởi chạy \"" + app.label + "\"");
    }

    private boolean isPackageBlocked(AppLockManager lockManager, boolean isKid, String pkg) {
        if (pkg == null || pkg.startsWith("system.")) return false;
        if (isKid && lockManager.isRestrictedInKidMode(pkg)) return true;
        return lockManager.isAppLocked(pkg) || lockManager.isVaultHidden(pkg);
    }

    private String getAppLabel(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }
}
