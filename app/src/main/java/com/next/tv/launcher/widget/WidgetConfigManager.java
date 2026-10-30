package com.next.tv.launcher.widget;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public final class WidgetConfigManager {
    private static final String PREFS_NAME = "tvhome_widget_settings";
    private static final String KEY_WEATHER = "widget_weather";
    private static final String KEY_NEWS = "widget_news";
    private static final String KEY_YOUTUBE = "widget_youtube";
    private static final String KEY_USAGE = "widget_usage";
    private static final String KEY_MEDIA = "widget_media";
    private static final String KEY_FOOTBALL = "widget_football";
    private static final String KEY_ORDER = "widget_order_list";

    private static WidgetConfigManager instance;
    private final SharedPreferences prefs;

    public static synchronized WidgetConfigManager getInstance(Context context) {
        if (instance == null) {
            instance = new WidgetConfigManager(context.getApplicationContext());
        }
        return instance;
    }

    private WidgetConfigManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isWeatherEnabled() {
        return prefs.getBoolean(KEY_WEATHER, true);
    }

    public boolean isNewsEnabled() {
        return prefs.getBoolean(KEY_NEWS, true);
    }

    public boolean isYouTubeEnabled() {
        return prefs.getBoolean(KEY_YOUTUBE, true);
    }

    public boolean isUsageEnabled() {
        return prefs.getBoolean(KEY_USAGE, true);
    }

    public boolean isMediaEnabled() {
        return prefs.getBoolean(KEY_MEDIA, false);
    }

    public boolean isFootballEnabled() {
        return prefs.getBoolean(KEY_FOOTBALL, true);
    }

    public void setWeatherEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_WEATHER, enabled).apply();
    }

    public void setNewsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_NEWS, enabled).apply();
    }

    public void setYouTubeEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_YOUTUBE, enabled).apply();
    }

    public void setUsageEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_USAGE, enabled).apply();
    }

    public void setMediaEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_MEDIA, enabled).apply();
    }

    public void setFootballEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_FOOTBALL, enabled).apply();
    }

    public void disableAllWidgets() {
        prefs.edit()
                .putBoolean(KEY_WEATHER, false)
                .putBoolean(KEY_NEWS, false)
                .putBoolean(KEY_YOUTUBE, false)
                .putBoolean(KEY_USAGE, false)
                .putBoolean(KEY_MEDIA, false)
                .putBoolean(KEY_FOOTBALL, false)
                .apply();
    }

    public void enableAllWidgets() {
        prefs.edit()
                .putBoolean(KEY_WEATHER, true)
                .putBoolean(KEY_NEWS, true)
                .putBoolean(KEY_YOUTUBE, true)
                .putBoolean(KEY_USAGE, true)
                .putBoolean(KEY_MEDIA, true)
                .putBoolean(KEY_FOOTBALL, true)
                .apply();
    }

    public void enableEssentialWidgetsOnly() {
        prefs.edit()
                .putBoolean(KEY_WEATHER, true)
                .putBoolean(KEY_NEWS, false)
                .putBoolean(KEY_YOUTUBE, false)
                .putBoolean(KEY_USAGE, true)
                .putBoolean(KEY_MEDIA, true)
                .putBoolean(KEY_FOOTBALL, false)
                .apply();
    }

    public List<Integer> getActiveWidgetTypes(boolean isMediaPlaying) {
        List<Integer> list = new ArrayList<>();
        String orderStr = prefs.getString(KEY_ORDER, "0,3,4,1,2,5");
        if (!orderStr.contains("5")) {
            orderStr = orderStr + ",5";
            prefs.edit().putString(KEY_ORDER, orderStr).apply();
        }
        String[] parts = orderStr.split(",");
        for (String p : parts) {
            try {
                int type = Integer.parseInt(p.trim());
                if (type == WidgetSectionAdapter.TYPE_WEATHER && isWeatherEnabled()) {
                    list.add(type);
                } else if (type == WidgetSectionAdapter.TYPE_MEDIA && isMediaEnabled() && isMediaPlaying) {
                    list.add(type);
                } else if (type == WidgetSectionAdapter.TYPE_YOUTUBE && isYouTubeEnabled()) {
                    list.add(type);
                } else if (type == WidgetSectionAdapter.TYPE_NEWS && isNewsEnabled()) {
                    list.add(type);
                } else if (type == WidgetSectionAdapter.TYPE_USAGE && isUsageEnabled()) {
                    list.add(type);
                } else if (type == WidgetSectionAdapter.TYPE_FOOTBALL && isFootballEnabled()) {
                    list.add(type);
                }
            } catch (Exception ignored) {}
        }
        return list;
    }

    public void moveWidgetOrder(int fromIndex, int toIndex, boolean isMediaPlaying) {
        List<Integer> active = getActiveWidgetTypes(isMediaPlaying);
        if (fromIndex < 0 || fromIndex >= active.size() || toIndex < 0 || toIndex >= active.size()) return;
        int item = active.remove(fromIndex);
        active.add(toIndex, item);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < active.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(active.get(i));
        }
        // Append disabled types to keep them in order string
        int[] all = {0, 3, 4, 1, 2, 5};
        for (int t : all) {
            if (!active.contains(t)) {
                sb.append(",").append(t);
            }
        }
        prefs.edit().putString(KEY_ORDER, sb.toString()).apply();
    }
    public void saveWidgetOrder(List<Integer> activeOrder) {
        if (activeOrder == null || activeOrder.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < activeOrder.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(activeOrder.get(i));
        }
        int[] all = {0, 3, 4, 1, 2, 5};
        for (int t : all) {
            if (!activeOrder.contains(t)) {
                sb.append(",").append(t);
            }
        }
        prefs.edit().putString(KEY_ORDER, sb.toString()).apply();
    }
}
