package com.next.tv.launcher.widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages installed widget addons, local cache persistence, and official store catalog.
 */
public final class WidgetAddonManager {
    private static final String TAG = "NextTV.WidgetAddon";
    private static final String PREFS_NAME = "widget_addon_store_prefs";
    private static final String KEY_INSTALLED_ADDONS = "installed_addons_json";
    private static final String KEY_ADDON_ORDER = "installed_addon_order";
    private static final String KEY_CATALOG_CACHE = "cached_catalog_json";

    public interface CatalogCallback {
        void onCatalogLoaded(List<WidgetAddon> catalog, boolean isSuccess);
    }

    public interface InstallCallback {
        void onInstallResult(boolean success, String message, WidgetAddon addon);
    }

    public interface OnAddonsChangedListener {
        void onAddonsChanged();
    }

    private static volatile WidgetAddonManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final List<OnAddonsChangedListener> listeners = new CopyOnWriteArrayList<>();

    public static WidgetAddonManager getInstance(Context context) {
        if (instance == null) {
            synchronized (WidgetAddonManager.class) {
                if (instance == null) {
                    instance = new WidgetAddonManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private WidgetAddonManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "NextTV-AddonManager");
            t.setDaemon(true);
            return t;
        });
        this.mainHandler = new Handler(Looper.getMainLooper());
        ensureDefaultsInitialized();
    }

    public void addListener(OnAddonsChangedListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(OnAddonsChangedListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    private void notifyListeners() {
        mainHandler.post(() -> {
            for (OnAddonsChangedListener l : listeners) {
                try {
                    l.onAddonsChanged();
                } catch (Throwable ignored) {}
            }
        });
    }

    /**
     * Initializes core built-in widgets if running for the first time.
     */
    private synchronized void ensureDefaultsInitialized() {
        if (prefs.contains(KEY_INSTALLED_ADDONS)) return;

        List<WidgetAddon> defaults = new ArrayList<>();
        defaults.add(new WidgetAddon("com.nexttv.widget.weather", "Thời tiết", "1.0.0", "NextTV Labs", "Dự báo thời tiết địa phương", "", "", "hero_metric", 10800, true, true, true));
        defaults.add(new WidgetAddon("com.nexttv.widget.sports", "Tỉ số thể thao", "1.0.0", "NextTV Labs", "Tỉ số trực tiếp giải đấu hàng đầu", "", "", "scoreboard", 60, true, true, true));
        defaults.add(new WidgetAddon("com.nexttv.widget.news", "Tin tức nóng", "1.0.0", "NextTV Labs", "Điểm tin nóng đa nguồn liên tục", "", "", "news_carousel", 3600, true, true, true));
        defaults.add(new WidgetAddon("com.nexttv.widget.youtube", "Video thịnh hành", "1.0.0", "NextTV Labs", "Video nổi bật trên YouTube", "", "", "media_banner", 21600, true, true, true));
        defaults.add(new WidgetAddon("com.nexttv.widget.usage", "Thời gian xem TV", "1.0.0", "NextTV Labs", "Thống kê thời lượng xem TV", "", "", "hero_metric", 3600, true, true, true));
        defaults.add(new WidgetAddon("com.nexttv.widget.media", "Đang phát nhạc", "1.0.0", "NextTV Labs", "Trình phát nhạc đang hoạt động", "", "", "media_session", 0, true, true, false));

        saveInstalledAddons(defaults);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < defaults.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(defaults.get(i).getId());
        }
        prefs.edit().putString(KEY_ADDON_ORDER, sb.toString()).apply();
    }

    /**
     * Gets all installed widgets in user-defined order.
     */
    public synchronized List<WidgetAddon> getInstalledAddons() {
        Map<String, WidgetAddon> map = new LinkedHashMap<>();
        String jsonStr = prefs.getString(KEY_INSTALLED_ADDONS, "[]");
        try {
            JSONArray arr = new JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj != null) {
                    WidgetAddon addon = WidgetAddon.fromJson(obj);
                    if (addon != null && !addon.getId().isEmpty()) {
                        map.put(addon.getId(), addon);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed reading installed addons: " + e.getMessage());
        }

        String orderStr = prefs.getString(KEY_ADDON_ORDER, "");
        List<WidgetAddon> ordered = new ArrayList<>();
        if (!orderStr.isEmpty()) {
            String[] parts = orderStr.split(",");
            for (String id : parts) {
                String cleanId = id.trim();
                if (map.containsKey(cleanId)) {
                    ordered.add(map.remove(cleanId));
                }
            }
        }
        // Append any remaining
        ordered.addAll(map.values());
        return ordered;
    }

    /**
     * Returns enabled addon IDs for dynamic batch feed.
     */
    public synchronized List<String> getActiveEnabledAddonIds() {
        List<String> list = new ArrayList<>();
        for (WidgetAddon addon : getInstalledAddons()) {
            if (addon.isEnabled()) {
                String id = addon.getId();
                // Map to simple shorthand if built-in
                if ("com.nexttv.widget.weather".equals(id)) list.add("weather");
                else if ("com.nexttv.widget.sports".equals(id)) list.add("sports");
                else if ("com.nexttv.widget.news".equals(id)) list.add("news");
                else if ("com.nexttv.widget.youtube".equals(id)) list.add("youtube");
                else if ("com.nexttv.widget.gold".equals(id)) list.add("gold");
                else if ("com.nexttv.widget.crypto".equals(id)) list.add("crypto");
                else if ("com.nexttv.widget.vietnam_calendar".equals(id)) list.add("vietnam_calendar");
                else list.add(id);
            }
        }
        return list;
    }

    private synchronized void saveInstalledAddons(List<WidgetAddon> list) {
        JSONArray arr = new JSONArray();
        for (WidgetAddon a : list) {
            arr.put(a.toJson());
        }
        prefs.edit().putString(KEY_INSTALLED_ADDONS, arr.toString()).apply();
    }

    public synchronized boolean isInstalled(String addonId) {
        for (WidgetAddon a : getInstalledAddons()) {
            if (a.getId().equalsIgnoreCase(addonId)) {
                return true;
            }
        }
        return false;
    }

    public synchronized void toggleAddon(String addonId, boolean enabled) {
        List<WidgetAddon> list = getInstalledAddons();
        for (WidgetAddon a : list) {
            if (a.getId().equalsIgnoreCase(addonId)) {
                a.setEnabled(enabled);
                break;
            }
        }
        saveInstalledAddons(list);
        notifyListeners();
    }

    public synchronized boolean uninstallAddon(String addonId) {
        List<WidgetAddon> list = getInstalledAddons();
        boolean removed = false;
        Iterator<WidgetAddon> it = list.iterator();
        while (it.hasNext()) {
            WidgetAddon a = it.next();
            if (a.getId().equalsIgnoreCase(addonId)) {
                if (a.isBuiltIn()) {
                    // Built-in widgets can only be disabled, not removed
                    a.setEnabled(false);
                } else {
                    it.remove();
                }
                removed = true;
                break;
            }
        }
        if (removed) {
            saveInstalledAddons(list);
            saveOrderFromList(list);
            notifyListeners();
        }
        return removed;
    }

    public synchronized void saveOrderFromList(List<WidgetAddon> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(list.get(i).getId());
        }
        prefs.edit().putString(KEY_ADDON_ORDER, sb.toString()).apply();
        notifyListeners();
    }

    public synchronized void moveAddonOrder(int fromIndex, int toIndex) {
        List<WidgetAddon> list = getInstalledAddons();
        if (fromIndex < 0 || fromIndex >= list.size() || toIndex < 0 || toIndex >= list.size()) return;
        WidgetAddon item = list.remove(fromIndex);
        list.add(toIndex, item);
        saveOrderFromList(list);
    }

    /**
     * Fetches official store catalog from backend /api/v1/addons/store.
     */
    public void fetchStoreCatalog(CatalogCallback callback) {
        executor.execute(() -> {
            String baseUrl = "https://nexttv.vixlab.workers.dev";
            String url = baseUrl + "/api/v1/addons/store";
            String rawJson = WidgetProtocolEngine.getInstance(context).safeHttpGet(url, 0);

            if (rawJson == null || rawJson.isEmpty()) {
                // Fallback to cache
                rawJson = prefs.getString(KEY_CATALOG_CACHE, null);
            } else {
                prefs.edit().putString(KEY_CATALOG_CACHE, rawJson).apply();
            }

            List<WidgetAddon> catalog = new ArrayList<>();
            boolean success = false;
            if (rawJson != null && !rawJson.isEmpty()) {
                try {
                    JSONObject root = new JSONObject(rawJson);
                    JSONArray arr = root.optJSONArray("widgets");
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.optJSONObject(i);
                            if (obj != null) {
                                WidgetAddon addon = WidgetAddon.fromJson(obj);
                                if (addon != null && !addon.getId().isEmpty()) {
                                    addon.setInstalled(isInstalled(addon.getId()));
                                    catalog.add(addon);
                                }
                            }
                        }
                        success = true;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed parsing store catalog: " + e.getMessage());
                }
            }

            final boolean finalSuccess = success;
            mainHandler.post(() -> {
                if (callback != null) callback.onCatalogLoaded(catalog, finalSuccess);
            });
        });
    }

    /**
     * Installs addon from Store catalog.
     */
    public void installFromStore(WidgetAddon addon, InstallCallback callback) {
        if (addon == null || addon.getId().isEmpty()) {
            if (callback != null) callback.onInstallResult(false, "Tiện ích không hợp lệ", null);
            return;
        }

        executor.execute(() -> {
            List<WidgetAddon> list = getInstalledAddons();
            for (WidgetAddon a : list) {
                if (a.getId().equalsIgnoreCase(addon.getId())) {
                    a.setEnabled(true);
                    a.setInstalled(true);
                    saveInstalledAddons(list);
                    notifyListeners();
                    mainHandler.post(() -> {
                        if (callback != null) callback.onInstallResult(true, "Đã kích hoạt tiện ích", a);
                    });
                    return;
                }
            }

            // Add new
            addon.setInstalled(true);
            addon.setEnabled(true);
            list.add(addon);
            saveInstalledAddons(list);
            saveOrderFromList(list);
            notifyListeners();

            mainHandler.post(() -> {
                if (callback != null) callback.onInstallResult(true, "Cài đặt thành công", addon);
            });
        });
    }

    /**
     * Installs external addon from custom manifest URL.
     */
    public void installFromUrl(String manifestUrl, InstallCallback callback) {
        if (manifestUrl == null || manifestUrl.trim().isEmpty()) {
            if (callback != null) callback.onInstallResult(false, "Vui lòng nhập đường dẫn hợp lệ", null);
            return;
        }

        final String cleanUrl = manifestUrl.trim();
        executor.execute(() -> {
            String rawJson = WidgetProtocolEngine.getInstance(context).safeHttpGet(cleanUrl, 0);
            if (rawJson == null || rawJson.isEmpty()) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onInstallResult(false, "Không thể tải manifest từ URL", null);
                });
                return;
            }

            try {
                JSONObject obj = new JSONObject(rawJson);
                String id = obj.optString("id", "");
                String name = obj.optString("name", "");
                if (id.isEmpty() || name.isEmpty()) {
                    mainHandler.post(() -> {
                        if (callback != null) callback.onInstallResult(false, "Tệp manifest thiếu thông tin id hoặc name", null);
                    });
                    return;
                }

                WidgetAddon addon = WidgetAddon.fromJson(obj);
                if (addon == null) {
                    mainHandler.post(() -> {
                        if (callback != null) callback.onInstallResult(false, "Cấu trúc manifest không hợp lệ", null);
                    });
                    return;
                }

                List<WidgetAddon> list = getInstalledAddons();
                boolean exists = false;
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).getId().equalsIgnoreCase(id)) {
                        list.set(i, addon);
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    list.add(addon);
                }

                saveInstalledAddons(list);
                saveOrderFromList(list);
                notifyListeners();

                mainHandler.post(() -> {
                    if (callback != null) callback.onInstallResult(true, "Cài đặt tiện ích thành công", addon);
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onInstallResult(false, "Lỗi phân tích cú pháp manifest: " + e.getMessage(), null);
                });
            }
        });
    }
}
