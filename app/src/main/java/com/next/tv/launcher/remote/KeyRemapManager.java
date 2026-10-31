package com.next.tv.launcher.remote;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.widget.Toast;

import com.next.tv.launcher.rec.LiveTvChannelProvider;
import com.next.tv.launcher.MainActivity;
import com.next.tv.launcher.R;
import com.next.tv.launcher.clean.BackgroundAppCleaner;
import com.next.tv.launcher.search.GlobalSearchOverlayManager;

import java.util.ArrayList;
import java.util.List;

public final class KeyRemapManager {
    public static final String PREFS_NAME = "remap_key_prefs";

    public static final int TYPE_NONE = 0;
    public static final int TYPE_APP = 1;
    public static final int TYPE_CHANNEL = 2;
    public static final int TYPE_SETTINGS_SYSTEM = 3;
    public static final int TYPE_SETTINGS_WIFI = 4;
    public static final int TYPE_VOICE_SEARCH = 5;
    public static final int TYPE_CONTEXT_MENU = 6;
    public static final int TYPE_CLEAN_RAM = 7;
    public static final int TYPE_WALLPAPER = 8;
    public static final int TYPE_REMOTE_FILE = 9;

    public static final class RemapAction {
        public final int type;
        public final String payload;
        public final String label;

        public RemapAction(int type, String payload, String label) {
            this.type = type;
            this.payload = payload != null ? payload : "";
            this.label = label != null ? label : "";
        }

        public String encode() {
            return type + "|" + payload + "|" + label;
        }

        public static RemapAction decode(String str) {
            if (str == null || str.isEmpty()) return null;
            String[] parts = str.split("\\|", 3);
            try {
                int type = Integer.parseInt(parts[0]);
                String payload = parts.length > 1 ? parts[1] : "";
                String label = parts.length > 2 ? parts[2] : "";
                return new RemapAction(type, payload, label);
            } catch (Exception e) {
                return null;
            }
        }
    }

    public static final class ActionDef {
        public final String id;
        public final int type;
        public final String title;
        public final int iconRes;

        public ActionDef(String id, int type, String title, int iconRes) {
            this.id = id;
            this.type = type;
            this.title = title;
            this.iconRes = iconRes;
        }
    }

    public static final class KeyBinding {
        public final int scanCode;
        public final int keyCode;
        public final String keyName;

        public KeyBinding(int scanCode, int keyCode, String keyName) {
            this.scanCode = scanCode;
            this.keyCode = keyCode;
            this.keyName = keyName != null ? keyName : "";
        }
    }

    public static final class KeyTarget {
        public final String id;
        public final String title;
        public final int scanCode;
        public final int keyCode;
        public final int group;

        public KeyTarget(String id, String title, int scanCode, int keyCode, int group) {
            this.id = id;
            this.title = title;
            this.scanCode = scanCode;
            this.keyCode = keyCode;
            this.group = group;
        }
    }

    private static volatile KeyRemapManager instance;
    private final Context context;
    private final SharedPreferences prefs;

    // Fast O(1) in-memory lookup
    private final SparseArray<RemapAction> scancodeActions = new SparseArray<>();
    private final SparseArray<RemapAction> keycodeActions = new SparseArray<>();

    private volatile boolean isListeningMode = false;
    private OnKeyCapturedListener capturedListener;

    public interface OnKeyCapturedListener {
        void onKeyCaptured(int scanCode, int keyCode, String suggestedName);
    }

    private KeyRemapManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        reload();
    }

    public static KeyRemapManager getInstance(Context context) {
        if (instance == null) {
            synchronized (KeyRemapManager.class) {
                if (instance == null) {
                    instance = new KeyRemapManager(context);
                }
            }
        }
        return instance;
    }

    public static List<ActionDef> getDefaultActionDefs() {
        List<ActionDef> list = new ArrayList<>();
        list.add(new ActionDef("act_context_menu", TYPE_CONTEXT_MENU, "Mở menu ngữ cảnh", R.drawable.ic_menu));
        list.add(new ActionDef("act_voice_search", TYPE_VOICE_SEARCH, "Tìm kiếm giọng nói", R.drawable.ic_search_voice));
        list.add(new ActionDef("act_livetv", TYPE_CHANNEL, "Truyền hình", R.drawable.ic_livetv));
        list.add(new ActionDef("act_clean_ram", TYPE_CLEAN_RAM, "Dọn dẹp RAM", R.drawable.ic_clean_ram));
        list.add(new ActionDef("act_wallpaper", TYPE_WALLPAPER, "Đổi hình nền TV", R.drawable.ic_wallpaper_picker));
        list.add(new ActionDef("act_remote_file", TYPE_REMOTE_FILE, "Quản lý tệp", R.drawable.ic_remote_file));
        return list;
    }

    public synchronized void reload() {
        scancodeActions.clear();
        keycodeActions.clear();

        // 1. Action-First mappings
        for (ActionDef def : getDefaultActionDefs()) {
            KeyBinding kb = getKeyBindingForAction(def.id);
            if (kb != null && (kb.scanCode > 0 || kb.keyCode > 0)) {
                String payload = getActionPayload(def.id);
                String label = getActionLabel(def.id);
                if (label == null || label.isEmpty()) label = def.title;
                RemapAction action = new RemapAction(def.type, payload, label);
                if (kb.scanCode > 0) scancodeActions.put(kb.scanCode, action);
                if (kb.keyCode > 0) keycodeActions.put(kb.keyCode, action);
            }
        }

        // 1b. Dynamic app mappings (act_app_*)
        java.util.Map<String, ?> all = prefs.getAll();
        for (String k : all.keySet()) {
            if (k.startsWith("map_act_act_app_") || k.equals("map_act_act_app")) {
                String actId = k.substring("map_act_".length());
                KeyBinding kb = getKeyBindingForAction(actId);
                if (kb != null && (kb.scanCode > 0 || kb.keyCode > 0)) {
                    String payload = getActionPayload(actId);
                    String label = getActionLabel(actId);
                    if (label == null || label.isEmpty()) label = "Ứng dụng";
                    RemapAction action = new RemapAction(TYPE_APP, payload, label);
                    if (kb.scanCode > 0) scancodeActions.put(kb.scanCode, action);
                    if (kb.keyCode > 0) keycodeActions.put(kb.keyCode, action);
                }
            }
        }

        // 2. Legacy key target mappings
        for (KeyTarget target : getDefaultKeyTargets()) {
            String val = prefs.getString(target.id, null);
            if (val != null) {
                RemapAction action = RemapAction.decode(val);
                if (action != null && action.type != TYPE_NONE) {
                    if (target.scanCode > 0 && scancodeActions.get(target.scanCode) == null) {
                        scancodeActions.put(target.scanCode, action);
                    }
                    if (target.keyCode > 0 && keycodeActions.get(target.keyCode) == null) {
                        keycodeActions.put(target.keyCode, action);
                    }
                }
            }
        }
    }

    public synchronized List<ActionDef> getAllAppActionDefs() {
        List<ActionDef> list = new ArrayList<>();
        java.util.Map<String, ?> all = prefs.getAll();
        for (String k : all.keySet()) {
            if (k.startsWith("map_act_act_app_")) {
                String actId = k.substring("map_act_".length());
                String label = getActionLabel(actId);
                if (label == null || label.isEmpty()) label = "Ứng dụng";
                list.add(new ActionDef(actId, TYPE_APP, label, R.drawable.ic_apps));
            } else if (k.equals("map_act_act_app")) {
                String label = getActionLabel("act_app");
                if (label == null || label.isEmpty()) label = "Mở ứng dụng";
                list.add(new ActionDef("act_app", TYPE_APP, label, R.drawable.ic_apps));
            }
        }
        return list;
    }

    public synchronized ActionDef findConflictingAction(int scanCode, int keyCode) {
        for (ActionDef def : getDefaultActionDefs()) {
            KeyBinding kb = getKeyBindingForAction(def.id);
            if (kb != null) {
                if ((scanCode > 0 && kb.scanCode == scanCode) || (keyCode > 0 && kb.keyCode == keyCode)) {
                    return def;
                }
            }
        }
        for (ActionDef def : getAllAppActionDefs()) {
            KeyBinding kb = getKeyBindingForAction(def.id);
            if (kb != null) {
                if ((scanCode > 0 && kb.scanCode == scanCode) || (keyCode > 0 && kb.keyCode == keyCode)) {
                    return def;
                }
            }
        }
        return null;
    }

    public KeyBinding getKeyBindingForAction(String actionId) {
        String val = prefs.getString("map_act_" + actionId, null);
        if (val == null || val.isEmpty()) return null;
        String[] parts = val.split(":", 3);
        try {
            int scan = Integer.parseInt(parts[0]);
            int key = Integer.parseInt(parts[1]);
            String name = parts.length > 2 ? parts[2] : "";
            return new KeyBinding(scan, key, name);
        } catch (Exception e) {
            return null;
        }
    }

    public synchronized void assignKeyToAction(String actionId, int scanCode, int keyCode, String keyName, String payload, String label) {
        ActionDef conflict = findConflictingAction(scanCode, keyCode);
        if (conflict != null && !conflict.id.equals(actionId)) {
            unassignAction(conflict.id);
        }

        prefs.edit().putString("map_act_" + actionId, scanCode + ":" + keyCode + ":" + keyName).apply();
        if (payload != null) {
            prefs.edit().putString("payload_act_" + actionId, payload).apply();
        }
        if (label != null) {
            prefs.edit().putString("label_act_" + actionId, label).apply();
        }
        reload();
    }

    public synchronized void unassignAction(String actionId) {
        prefs.edit()
                .remove("map_act_" + actionId)
                .remove("payload_act_" + actionId)
                .remove("label_act_" + actionId)
                .apply();
        reload();
    }

    public String getActionPayload(String actionId) {
        return prefs.getString("payload_act_" + actionId, "");
    }

    public String getActionLabel(String actionId) {
        return prefs.getString("label_act_" + actionId, "");
    }

    public RemapAction getAction(int scanCode, int keyCode) {
        if (scanCode > 0) {
            RemapAction act = scancodeActions.get(scanCode);
            if (act != null) return act;
        }
        if (keyCode > 0) {
            return keycodeActions.get(keyCode);
        }
        return null;
    }

    public RemapAction getActionForTarget(KeyTarget target) {
        if (target == null) return null;
        String val = prefs.getString(target.id, null);
        return RemapAction.decode(val);
    }

    public void setActionForTarget(KeyTarget target, RemapAction action) {
        if (target == null) return;
        if (action == null || action.type == TYPE_NONE) {
            prefs.edit().remove(target.id).apply();
            if (target.scanCode > 0) scancodeActions.remove(target.scanCode);
            if (target.keyCode > 0) keycodeActions.remove(target.keyCode);
        } else {
            prefs.edit().putString(target.id, action.encode()).apply();
            if (target.scanCode > 0) scancodeActions.put(target.scanCode, action);
            if (target.keyCode > 0) keycodeActions.put(target.keyCode, action);
        }
    }

    public boolean isListeningMode() {
        return isListeningMode;
    }

    public void startListeningMode(OnKeyCapturedListener listener) {
        this.isListeningMode = true;
        this.capturedListener = listener;
    }

    public void stopListeningMode() {
        this.isListeningMode = false;
        this.capturedListener = null;
    }

    public static boolean isSystemForbiddenKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_HOME
                || keyCode == KeyEvent.KEYCODE_BACK
                || keyCode == KeyEvent.KEYCODE_ESCAPE
                || keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
                || keyCode == KeyEvent.KEYCODE_POWER;
    }

    public boolean handleKeyEventIfListening(KeyEvent event) {
        if (!isListeningMode || capturedListener == null || event == null) return false;
        if (event.getAction() != KeyEvent.ACTION_UP) return true;

        int keyCode = event.getKeyCode();
        int scanCode = event.getScanCode();

        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            stopListeningMode();
            return false;
        }

        if (isSystemForbiddenKey(keyCode)) {
            return true;
        }

        String name = resolveKeyName(scanCode, keyCode);
        OnKeyCapturedListener listener = this.capturedListener;
        stopListeningMode();
        if (listener != null) {
            listener.onKeyCaptured(scanCode, keyCode, name);
        }
        return true;
    }

    public static String resolveKeyName(int scanCode, int keyCode) {
        // Scancodes
        if (scanCode == 0x0069 || scanCode == 105) return "Phím Đỏ";
        if (scanCode == 0x006a || scanCode == 106) return "Phím Xanh lá";
        if (scanCode == 0x006c || scanCode == 108) return "Phím Vàng";
        if (scanCode == 0x006b || scanCode == 107) return "Phím Xanh dương";

        if (scanCode == 0x0077 || scanCode == 119) return "Phím 119";
        if (scanCode == 0x0078 || scanCode == 120) return "Phím 120";

        if (scanCode == 0x0061 || scanCode == 97) return "Phím Phụ đề";
        if (scanCode == 0x01bd || scanCode == 445) return "Phím Thông tin";
        if (scanCode == 0x0096 || scanCode == 150) return "Phím Băng từ";
        if (scanCode == 0x008d || scanCode == 141) return "Phím Chương trình";
        if (scanCode == 0x022a || scanCode == 554) return "Phím Dấu trang";

        // KeyCodes
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            return "Phím số " + (keyCode - KeyEvent.KEYCODE_0);
        }
        if (keyCode == KeyEvent.KEYCODE_PROG_RED) return "Phím Đỏ";
        if (keyCode == KeyEvent.KEYCODE_PROG_GREEN) return "Phím Xanh lá";
        if (keyCode == KeyEvent.KEYCODE_PROG_YELLOW) return "Phím Vàng";
        if (keyCode == KeyEvent.KEYCODE_PROG_BLUE) return "Phím Xanh dương";
        if (keyCode == KeyEvent.KEYCODE_INFO) return "Phím Thông tin";
        if (keyCode == KeyEvent.KEYCODE_CAPTIONS) return "Phím Phụ đề";
        if (keyCode == KeyEvent.KEYCODE_BOOKMARK) return "Phím Dấu trang";

        if (scanCode > 0) {
            return "Phím " + scanCode;
        }
        return "Phím " + keyCode;
    }

    public boolean execute(Context ctx, RemapAction action) {
        if (action == null || action.type == TYPE_NONE) return false;

        switch (action.type) {
            case TYPE_APP:
                try {
                    Intent intent = ctx.getPackageManager().getLeanbackLaunchIntentForPackage(action.payload);
                    if (intent == null) {
                        intent = ctx.getPackageManager().getLaunchIntentForPackage(action.payload);
                    }
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        ctx.startActivity(intent);
                        return true;
                    }
                } catch (Exception ignored) {}
                break;

            case TYPE_CHANNEL:
                if (action.payload != null && !action.payload.isEmpty()) {
                    try {
                        Intent intent = Intent.parseUri(action.payload, Intent.URI_INTENT_SCHEME);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        ctx.startActivity(intent);
                        return true;
                    } catch (Exception ignored) {}
                }
                // Fallback to installed TV app
                String[] tvPkgs = new String[]{
                        LiveTvChannelProvider.PKG_VTVGO,
                        LiveTvChannelProvider.PKG_XEMTV,
                        LiveTvChannelProvider.PKG_TV360,
                        LiveTvChannelProvider.PKG_FPT,
                        LiveTvChannelProvider.PKG_MYTV,
                        LiveTvChannelProvider.PKG_SCTV
                };
                for (String pkg : tvPkgs) {
                    if (LiveTvChannelProvider.isPackageInstalled(ctx, pkg)) {
                        Intent intent = ctx.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
                        if (intent == null) intent = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
                        if (intent != null) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            ctx.startActivity(intent);
                            return true;
                        }
                    }
                }
                Toast.makeText(ctx, "Chưa cài đặt app TV", Toast.LENGTH_SHORT).show();
                return true;

            case TYPE_SETTINGS_SYSTEM:
                try {
                    Intent sys = new Intent(Settings.ACTION_SETTINGS);
                    sys.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(sys);
                    return true;
                } catch (Exception ignored) {}
                break;

            case TYPE_SETTINGS_WIFI:
                try {
                    Intent wifi = new Intent(Settings.ACTION_WIFI_SETTINGS);
                    wifi.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(wifi);
                    return true;
                } catch (Exception ignored) {}
                break;

            case TYPE_VOICE_SEARCH:
                try {
                    GlobalSearchOverlayManager.getInstance(ctx).show(ctx, true);
                    return true;
                } catch (Exception ignored) {}
                break;

            case TYPE_CONTEXT_MENU:
                if (MainActivity.activeInstance != null) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (MainActivity.activeInstance != null) {
                            MainActivity.activeInstance.triggerFocusedContextMenu();
                        }
                    });
                    return true;
                }
                break;

            case TYPE_CLEAN_RAM:
                BackgroundAppCleaner.cleanRam(ctx);
                Toast.makeText(ctx, "Đã tối ưu bộ nhớ", Toast.LENGTH_SHORT).show();
                return true;

            case TYPE_WALLPAPER:
                if (MainActivity.activeInstance != null) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (MainActivity.activeInstance != null) {
                            MainActivity.activeInstance.showWallpaperPicker();
                        }
                    });
                    return true;
                }
                break;

            case TYPE_REMOTE_FILE:
                if (MainActivity.activeInstance != null) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (MainActivity.activeInstance != null) {
                            MainActivity.activeInstance.showRemoteFileDrawer();
                        }
                    });
                    return true;
                }
                break;
        }
        return false;
    }

    public static List<KeyTarget> getDefaultKeyTargets() {
        List<KeyTarget> list = new ArrayList<>();
        list.add(new KeyTarget("key_red", "Phím Đỏ", 105, KeyEvent.KEYCODE_PROG_RED, 0));
        list.add(new KeyTarget("key_green", "Phím Xanh lá", 106, KeyEvent.KEYCODE_PROG_GREEN, 0));
        list.add(new KeyTarget("key_yellow", "Phím Vàng", 108, KeyEvent.KEYCODE_PROG_YELLOW, 0));
        list.add(new KeyTarget("key_blue", "Phím Xanh dương", 107, KeyEvent.KEYCODE_PROG_BLUE, 0));
        return list;
    }
}
