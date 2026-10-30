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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

/**
 * Manages installed widget addons, local cache persistence, and official store catalog.
 * Serves as the Single Source of Truth for all widgets and addons across NextTV Launcher.
 */
public final class WidgetAddonManager {
    private static final String TAG = "NextTV.WidgetAddon";
    private static final String PREFS_NAME = "widget_addon_store_prefs";
    private static final String KEY_INSTALLED_ADDONS = "installed_addons_json";
    private static final String KEY_ADDON_ORDER = "installed_addon_order";
    private static final String KEY_CATALOG_CACHE = "cached_catalog_json";

    public static final String ID_WEATHER = "com.nexttv.widget.weather";
    public static final String ID_SPORTS = "com.nexttv.widget.sports";
    public static final String ID_NEWS = "com.nexttv.widget.news";
    public static final String ID_YOUTUBE = "com.nexttv.widget.youtube";
    public static final String ID_USAGE = "com.nexttv.widget.usage";
    public static final String ID_MEDIA = "com.nexttv.widget.media";
    public static final String ID_GOLD = "com.nexttv.widget.gold";
    public static final String ID_CRYPTO = "com.nexttv.widget.crypto";
    public static final String ID_VIETNAM_CALENDAR = "com.nexttv.widget.vietnam_calendar";

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
    private List<WidgetAddon> cachedInstalledAddons = null;
    private final Map<Integer, WidgetAddon> cachedTypeToAddonMap = new HashMap<>();
    private final Map<String, Integer> cachedIdToTypeMap = new HashMap<>();

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
        defaults.add(new WidgetAddon(ID_WEATHER, "Thời tiết", "1.0.0", "NextTV Labs", "Dự báo thời tiết địa phương", "", "", "hero_metric", 10800, true, true, true));
        defaults.add(new WidgetAddon(ID_SPORTS, "Tỉ số thể thao", "1.0.0", "NextTV Labs", "Tỉ số trực tiếp giải đấu hàng đầu", "", "", "scoreboard", 60, true, true, true));
        defaults.add(new WidgetAddon(ID_NEWS, "Tin tức nóng", "1.0.0", "NextTV Labs", "Điểm tin nóng đa nguồn liên tục", "", "", "news_carousel", 3600, true, true, true));
        defaults.add(new WidgetAddon(ID_YOUTUBE, "Video thịnh hành", "1.0.0", "NextTV Labs", "Video nổi bật trên YouTube", "", "", "media_banner", 21600, true, true, true));
        defaults.add(new WidgetAddon(ID_USAGE, "Thời gian xem TV", "1.0.0", "NextTV Labs", "Thống kê thời lượng xem TV", "", "", "hero_metric", 3600, true, true, true));
        defaults.add(new WidgetAddon(ID_MEDIA, "Đang phát nhạc", "1.0.0", "NextTV Labs", "Trình phát nhạc đang hoạt động", "", "", "media_session", 0, true, true, false));

        saveInstalledAddons(defaults);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < defaults.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(defaults.get(i).getId());
        }
        prefs.edit().putString(KEY_ADDON_ORDER, sb.toString()).apply();
        ensureCacheLoaded();
    }

    private synchronized void ensureCacheLoaded() {
        if (cachedInstalledAddons != null) return;
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
        rebuildCacheLocked(ordered);
    }

    private void rebuildCacheLocked(List<WidgetAddon> list) {
        cachedInstalledAddons = new ArrayList<>(list);
        cachedTypeToAddonMap.clear();
        cachedIdToTypeMap.clear();
        for (WidgetAddon a : cachedInstalledAddons) {
            int type = getWidgetTypeForId(a.getId());
            cachedTypeToAddonMap.put(type, a);
            cachedIdToTypeMap.put(a.getId(), type);
        }
    }

    /**
     * Gets all installed widgets in user-defined order (instant in-memory copy).
     */
    public synchronized List<WidgetAddon> getInstalledAddons() {
        ensureCacheLoaded();
        return new ArrayList<>(cachedInstalledAddons);
    }

    /**
     * Maps an addon ID string to the corresponding integer widget type for WidgetSectionAdapter.
     * Guaranteed non-negative with >1,000,000,000 slots to avoid hash collision.
     */
    public static int getWidgetTypeForId(String id) {
        if (id == null) return WidgetSectionAdapter.TYPE_WEATHER;
        switch (id) {
            case ID_WEATHER: return WidgetSectionAdapter.TYPE_WEATHER;
            case ID_NEWS: return WidgetSectionAdapter.TYPE_NEWS;
            case ID_USAGE: return WidgetSectionAdapter.TYPE_USAGE;
            case ID_MEDIA: return WidgetSectionAdapter.TYPE_MEDIA;
            case ID_YOUTUBE: return WidgetSectionAdapter.TYPE_YOUTUBE;
            case ID_SPORTS: return WidgetSectionAdapter.TYPE_FOOTBALL;
            case ID_GOLD: return WidgetSectionAdapter.TYPE_GOLD;
            case ID_CRYPTO: return WidgetSectionAdapter.TYPE_CRYPTO;
            case ID_VIETNAM_CALENDAR: return WidgetSectionAdapter.TYPE_VIETNAM_CALENDAR;
            default:
                return 1000 + (id.hashCode() & 0x3fffffff);
        }
    }

    /**
     * Maps an integer widget type back to an addon ID string (O(1) memory lookup).
     */
    public synchronized String getIdForWidgetType(int type) {
        switch (type) {
            case WidgetSectionAdapter.TYPE_WEATHER: return ID_WEATHER;
            case WidgetSectionAdapter.TYPE_NEWS: return ID_NEWS;
            case WidgetSectionAdapter.TYPE_USAGE: return ID_USAGE;
            case WidgetSectionAdapter.TYPE_MEDIA: return ID_MEDIA;
            case WidgetSectionAdapter.TYPE_YOUTUBE: return ID_YOUTUBE;
            case WidgetSectionAdapter.TYPE_FOOTBALL: return ID_SPORTS;
            case WidgetSectionAdapter.TYPE_GOLD: return ID_GOLD;
            case WidgetSectionAdapter.TYPE_CRYPTO: return ID_CRYPTO;
            case WidgetSectionAdapter.TYPE_VIETNAM_CALENDAR: return ID_VIETNAM_CALENDAR;
            default:
                ensureCacheLoaded();
                WidgetAddon addon = cachedTypeToAddonMap.get(type);
                return addon != null ? addon.getId() : null;
        }
    }

    /**
     * Retrieves WidgetAddon by integer widget type directly in O(1).
     */
    public synchronized WidgetAddon getAddonForWidgetType(int type) {
        ensureCacheLoaded();
        return cachedTypeToAddonMap.get(type);
    }

    /**
     * Batch enables or disables addons according to a predicate rule.
     * Executes a single JSON serialization and single notification.
     */
    public synchronized void setAddonsEnabledBatch(Predicate<WidgetAddon> rule) {
        if (rule == null) return;
        ensureCacheLoaded();
        boolean changed = false;
        for (WidgetAddon a : cachedInstalledAddons) {
            boolean shouldEnable = rule.test(a);
            if (a.isEnabled() != shouldEnable) {
                a.setEnabled(shouldEnable);
                changed = true;
            }
        }
        if (changed) {
            saveInstalledAddons(cachedInstalledAddons);
            notifyListeners();
        }
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
                if (ID_WEATHER.equals(id)) list.add("weather");
                else if (ID_SPORTS.equals(id)) list.add("sports");
                else if (ID_NEWS.equals(id)) list.add("news");
                else if (ID_YOUTUBE.equals(id)) list.add("youtube");
                else if (ID_GOLD.equals(id)) list.add("gold");
                else if (ID_CRYPTO.equals(id)) list.add("crypto");
                else if (ID_VIETNAM_CALENDAR.equals(id)) list.add("vietnam_calendar");
                else list.add(id);
            }
        }
        return list;
    }

    private synchronized void saveInstalledAddons(List<WidgetAddon> list) {
        rebuildCacheLocked(list);
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

    public synchronized boolean isAddonEnabled(String addonId) {
        for (WidgetAddon a : getInstalledAddons()) {
            if (a.getId().equalsIgnoreCase(addonId)) {
                return a.isEnabled();
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
        rebuildCacheLocked(list);
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
     * Builds default store catalog when offline or before backend is synced.
     */
    public List<WidgetAddon> buildDefaultStoreCatalog() {
        List<WidgetAddon> catalog = new ArrayList<>();
        catalog.add(new WidgetAddon(ID_GOLD, "Giá vàng SJC 9999", "1.0.0", "NextTV Labs", "Giá vàng trong nước & thế giới liên tục cập nhật", "/addons/gold/manifest.json", "", "hero_metric", 1800, false, isInstalled(ID_GOLD), isAddonEnabled(ID_GOLD)));
        catalog.add(new WidgetAddon(ID_CRYPTO, "Tỷ giá Tiền số (Crypto)", "1.0.0", "NextTV Labs", "Biến động Bitcoin, ETH, SOL theo thời gian thực", "/addons/crypto/manifest.json", "", "hero_metric", 300, false, isInstalled(ID_CRYPTO), isAddonEnabled(ID_CRYPTO)));
        catalog.add(new WidgetAddon(ID_VIETNAM_CALENDAR, "Lịch Âm Dương Việt Nam", "1.0.0", "NextTV Labs", "Ngày âm, can chi, giờ hoàng đạo và tiết khí", "/addons/vietnam_calendar/manifest.json", "", "hero_metric", 21600, false, isInstalled(ID_VIETNAM_CALENDAR), isAddonEnabled(ID_VIETNAM_CALENDAR)));
        catalog.add(new WidgetAddon(ID_WEATHER, "Thời tiết", "1.0.0", "NextTV Labs", "Dự báo thời tiết địa phương tức thì", "", "", "hero_metric", 10800, true, true, isAddonEnabled(ID_WEATHER)));
        catalog.add(new WidgetAddon(ID_SPORTS, "Tỉ số thể thao", "1.0.0", "NextTV Labs", "Tỉ số trực tiếp các giải đấu hàng đầu", "", "", "scoreboard", 60, true, true, isAddonEnabled(ID_SPORTS)));
        catalog.add(new WidgetAddon(ID_NEWS, "Tin tức nóng", "1.0.0", "NextTV Labs", "Điểm tin nóng đa nguồn liên tục", "", "", "news_carousel", 3600, true, true, isAddonEnabled(ID_NEWS)));
        catalog.add(new WidgetAddon(ID_YOUTUBE, "Video thịnh hành", "1.0.0", "NextTV Labs", "Danh sách video nổi bật trên YouTube", "", "", "media_banner", 21600, true, true, isAddonEnabled(ID_YOUTUBE)));
        return catalog;
    }

    private List<WidgetAddon> parseCatalogJson(String rawJson) {
        List<WidgetAddon> catalog = new ArrayList<>();
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
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed parsing store catalog: " + e.getMessage());
            }
        }
        return catalog;
    }

    /**
     * Fetches official store catalog instantly (0ms latency) with background remote refresh.
     */
    public void fetchStoreCatalog(CatalogCallback callback) {
        // 1. Deliver local default or cached catalog immediately
        String cachedJson = prefs.getString(KEY_CATALOG_CACHE, null);
        List<WidgetAddon> instantCatalog = parseCatalogJson(cachedJson);
        if (instantCatalog.isEmpty()) {
            instantCatalog = buildDefaultStoreCatalog();
        }
        if (callback != null) {
            callback.onCatalogLoaded(instantCatalog, true);
        }

        // 2. Fetch remote update in background
        executor.execute(() -> {
            String baseUrl;
            try {
                baseUrl = com.next.tv.launcher.security.NativeSecurityBridge.getProxyBaseUrl(context);
            } catch (Throwable ignored) {
                baseUrl = null;
            }
            if (baseUrl == null || baseUrl.isEmpty()) {
                baseUrl = "https://nexttv.vixlab.workers.dev";
            }
            String url = baseUrl + "/api/v1/addons/store";
            String rawJson = WidgetProtocolEngine.getInstance(context).safeHttpGet(url, 0, "manifest");
            if (rawJson != null && !rawJson.isEmpty() && !rawJson.equals(cachedJson)) {
                prefs.edit().putString(KEY_CATALOG_CACHE, rawJson).apply();
                List<WidgetAddon> freshCatalog = parseCatalogJson(rawJson);
                if (!freshCatalog.isEmpty() && callback != null) {
                    mainHandler.post(() -> callback.onCatalogLoaded(freshCatalog, true));
                }
            }
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

            // Add new addon
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
            // External custom URLs do not send internal APK HMAC signature
            String rawJson = WidgetProtocolEngine.getInstance(context).safeHttpGet(cleanUrl, 0, null);
            if (rawJson == null || rawJson.isEmpty()) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onInstallResult(false, "Không thể tải cấu hình tiện ích từ liên kết", null);
                });
                return;
            }

            try {
                JSONObject obj = new JSONObject(rawJson);
                String id = obj.optString("id", "");
                String name = obj.optString("name", "");
                if (id.isEmpty() || name.isEmpty()) {
                    mainHandler.post(() -> {
                        if (callback != null) callback.onInstallResult(false, "Tệp cấu hình thiếu mã định danh hoặc tên tiện ích", null);
                    });
                    return;
                }

                WidgetAddon addon = WidgetAddon.fromJson(obj);
                if (addon == null) {
                    mainHandler.post(() -> {
                        if (callback != null) callback.onInstallResult(false, "Cấu trúc tệp cấu hình không hợp lệ", null);
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
                    if (callback != null) callback.onInstallResult(false, "Lỗi phân tích cú pháp cấu hình: " + e.getMessage(), null);
                });
            }
        });
    }
}
