package com.next.tv.launcher.widget;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * Backward-compatible bridge to WidgetAddonManager.
 * Delegates all widget configuration, order, and toggle operations to WidgetAddonManager
 * as the Single Source of Truth.
 */
public final class WidgetConfigManager {
    private static WidgetConfigManager instance;
    private final WidgetAddonManager addonManager;

    public static synchronized WidgetConfigManager getInstance(Context context) {
        if (instance == null) {
            instance = new WidgetConfigManager(context.getApplicationContext());
        }
        return instance;
    }

    private WidgetConfigManager(Context context) {
        this.addonManager = WidgetAddonManager.getInstance(context);
    }

    public boolean isWeatherEnabled() {
        return addonManager.isAddonEnabled(WidgetAddonManager.ID_WEATHER);
    }

    public boolean isNewsEnabled() {
        return addonManager.isAddonEnabled(WidgetAddonManager.ID_NEWS);
    }

    public boolean isYouTubeEnabled() {
        return addonManager.isAddonEnabled(WidgetAddonManager.ID_YOUTUBE);
    }

    public boolean isUsageEnabled() {
        return addonManager.isAddonEnabled(WidgetAddonManager.ID_USAGE);
    }

    public boolean isMediaEnabled() {
        return addonManager.isAddonEnabled(WidgetAddonManager.ID_MEDIA);
    }

    public boolean isFootballEnabled() {
        return addonManager.isAddonEnabled(WidgetAddonManager.ID_SPORTS);
    }

    public void setWeatherEnabled(boolean enabled) {
        addonManager.toggleAddon(WidgetAddonManager.ID_WEATHER, enabled);
    }

    public void setNewsEnabled(boolean enabled) {
        addonManager.toggleAddon(WidgetAddonManager.ID_NEWS, enabled);
    }

    public void setYouTubeEnabled(boolean enabled) {
        addonManager.toggleAddon(WidgetAddonManager.ID_YOUTUBE, enabled);
    }

    public void setUsageEnabled(boolean enabled) {
        addonManager.toggleAddon(WidgetAddonManager.ID_USAGE, enabled);
    }

    public void setMediaEnabled(boolean enabled) {
        addonManager.toggleAddon(WidgetAddonManager.ID_MEDIA, enabled);
    }

    public void setFootballEnabled(boolean enabled) {
        addonManager.toggleAddon(WidgetAddonManager.ID_SPORTS, enabled);
    }

    public void setGoldEnabled(boolean enabled) {
        addonManager.toggleAddon(WidgetAddonManager.ID_GOLD, enabled);
    }

    public void setCryptoEnabled(boolean enabled) {
        addonManager.toggleAddon(WidgetAddonManager.ID_CRYPTO, enabled);
    }

    public void setCalendarEnabled(boolean enabled) {
        addonManager.toggleAddon(WidgetAddonManager.ID_VIETNAM_CALENDAR, enabled);
    }

    public void disableAllWidgets() {
        addonManager.setAddonsEnabledBatch(a -> false);
    }

    public void enableAllWidgets() {
        addonManager.setAddonsEnabledBatch(a -> true);
    }

    public void enableEssentialWidgetsOnly() {
        addonManager.setAddonsEnabledBatch(a ->
                WidgetAddonManager.ID_WEATHER.equals(a.getId())
                        || WidgetAddonManager.ID_USAGE.equals(a.getId())
                        || WidgetAddonManager.ID_MEDIA.equals(a.getId())
        );
    }

    /**
     * Returns ordered list of active integer widget types directly from WidgetAddonManager.
     * Supports types 0 through 8 and dynamic external addons (type >= 100).
     */
    public List<Integer> getActiveWidgetTypes(boolean isMediaPlaying) {
        List<Integer> list = new ArrayList<>();
        List<WidgetAddon> installed = addonManager.getInstalledAddons();
        for (WidgetAddon addon : installed) {
            if (!addon.isEnabled()) continue;
            if (WidgetAddonManager.ID_MEDIA.equals(addon.getId()) && !isMediaPlaying) continue;

            int type = WidgetAddonManager.getWidgetTypeForId(addon.getId());
            list.add(type);
        }
        return list;
    }

    public void saveWidgetOrder(List<Integer> activeOrder) {
        if (activeOrder == null || activeOrder.isEmpty()) return;
        List<WidgetAddon> installed = addonManager.getInstalledAddons();
        java.util.Map<String, WidgetAddon> map = new java.util.LinkedHashMap<>();
        for (WidgetAddon a : installed) {
            map.put(a.getId(), a);
        }

        List<WidgetAddon> reordered = new ArrayList<>();
        for (int type : activeOrder) {
            String id = addonManager.getIdForWidgetType(type);
            if (id != null && map.containsKey(id)) {
                reordered.add(map.remove(id));
            }
        }

        // Keep disabled or remaining addons in order
        reordered.addAll(map.values());
        addonManager.saveOrderFromList(reordered);
    }
}
