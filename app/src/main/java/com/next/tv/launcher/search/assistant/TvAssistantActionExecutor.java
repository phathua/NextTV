package com.next.tv.launcher.search.assistant;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes system-level controls (Volume, Bluetooth, App Launching, Deep-link search)
 * safely on Android TV with appropriate error handling and fallback behaviors.
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

    public TvAssistantActionExecutor(Context context) {
        this.context = context.getApplicationContext();
        this.audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
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
            int currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

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
     * Resolves app from query and launches it immediately.
     */
    public SystemActionResult executeLaunchApp(String appQuery) {
        if (appQuery == null || appQuery.trim().isEmpty()) {
            return new SystemActionResult(false, "Chưa rõ tên ứng dụng");
        }

        String normQuery = TvAssistantDispatcher.stripDiacritics(appQuery);
        PackageManager pm = context.getPackageManager();

        // 1. Check alias dictionary
        for (Map.Entry<String, String[]> entry : APP_ALIASES.entrySet()) {
            if (normQuery.contains(entry.getKey())) {
                for (String pkg : entry.getValue()) {
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

        // 2. Query all installed leanback & standard launcher activities
        Intent leanbackIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> activities = pm.queryIntentActivities(leanbackIntent, 0);

        Intent standardIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> standardActivities = pm.queryIntentActivities(standardIntent, 0);
        activities.addAll(standardActivities);

        ResolveInfo bestMatch = null;
        int bestScore = 0;

        for (ResolveInfo info : activities) {
            if (info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            CharSequence labelSeq = info.loadLabel(pm);
            String label = labelSeq != null ? labelSeq.toString() : "";
            String normLabel = TvAssistantDispatcher.stripDiacritics(label);

            if (normLabel.equals(normQuery)) {
                bestMatch = info;
                break;
            } else if (normLabel.contains(normQuery) || normQuery.contains(normLabel)) {
                int score = normLabel.length();
                if (score > bestScore) {
                    bestScore = score;
                    bestMatch = info;
                }
            }
        }

        if (bestMatch != null && bestMatch.activityInfo != null) {
            String pkg = bestMatch.activityInfo.packageName;
            Intent intent = pm.getLaunchIntentForPackage(pkg);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                CharSequence label = bestMatch.loadLabel(pm);
                return new SystemActionResult(true, "Đang mở " + (label != null ? label : pkg));
            }
        }

        return new SystemActionResult(false, "Không tìm thấy ứng dụng \"" + appQuery + "\"");
    }

    private String getAppLabel(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return null;
        }
    }
}
