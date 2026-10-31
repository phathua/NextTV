package com.next.tv.launcher.rec;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Manages user preferences for home screen sections (order, visibility).
 */
public final class SectionConfig {
    private static final String PREFS_NAME = "home_sections_config";
    private static final String KEY_SECTIONS_ORDER = "sections_order";
    private static final String KEY_SECTION_ENABLED_PREFIX = "section_enabled_";

    public static final String SECTION_APPS = "section_apps";
    public static final String SECTION_CONTINUE_WATCHING = "watch_next_unified";
    public static final String SECTION_LIVE_TV = "live_channels";

    private final SharedPreferences prefs;

    public SectionConfig(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Get ordered list of section IDs.
     */
    public List<String> getSectionOrder() {
        String saved = prefs.getString(KEY_SECTIONS_ORDER, null);
        if (saved != null && !saved.isEmpty()) {
            return new ArrayList<>(Arrays.asList(saved.split(",")));
        }
        // Default order: apps first, then continue watching, then live channels
        List<String> def = new ArrayList<>();
        def.add(SECTION_APPS);
        def.add(SECTION_CONTINUE_WATCHING);
        def.add(SECTION_LIVE_TV);
        return def;
    }

    /**
     * Save updated section order.
     */
    public void saveSectionOrder(List<String> order) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < order.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(order.get(i));
        }
        prefs.edit().putString(KEY_SECTIONS_ORDER, sb.toString()).commit();
    }

    /**
     * Check if a specific section is enabled.
     */
    public boolean isSectionEnabled(String sectionId) {
        // Apps section is always enabled
        if (SECTION_APPS.equals(sectionId)) return true;
        return prefs.getBoolean(KEY_SECTION_ENABLED_PREFIX + sectionId, true);
    }

    /**
     * Toggle section enabled state.
     */
    public void setSectionEnabled(String sectionId, boolean enabled) {
        prefs.edit().putBoolean(KEY_SECTION_ENABLED_PREFIX + sectionId, enabled).commit();
    }
}
