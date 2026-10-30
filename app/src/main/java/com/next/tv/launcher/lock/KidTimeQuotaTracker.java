package com.next.tv.launcher.lock;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

/**
 * Tracks daily kid viewing allowances, curfew sleep windows, and 5-minute warnings.
 */
public final class KidTimeQuotaTracker {
    private static final String PREF_NAME = "kid_time_quota_prefs";
    private static final String KEY_DAILY_QUOTA_MINUTES = "kid_daily_quota_minutes"; // 0 = unlimited, 30, 60, 90, 120
    private static final String KEY_CURFEW_ENABLED = "kid_curfew_enabled";
    private static final String KEY_CURFEW_START_HOUR = "kid_curfew_start_hour"; // default 21 (9 PM)
    private static final String KEY_CURFEW_END_HOUR = "kid_curfew_end_hour";     // default 6 (6 AM)
    private static final String KEY_BONUS_MINUTES = "kid_bonus_minutes";
    private static final String KEY_BONUS_DATE = "kid_bonus_date";
    private static final String KEY_WARNING_SHOWN_DATE = "kid_warning_shown_date";

    private static KidTimeQuotaTracker instance;
    private final SharedPreferences prefs;

    private KidTimeQuotaTracker(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized KidTimeQuotaTracker getInstance(Context context) {
        if (instance == null) {
            instance = new KidTimeQuotaTracker(context);
        }
        return instance;
    }

    public int getDailyQuotaMinutes() {
        return prefs.getInt(KEY_DAILY_QUOTA_MINUTES, 60); // Default 60 minutes
    }

    public void setDailyQuotaMinutes(int minutes) {
        prefs.edit().putInt(KEY_DAILY_QUOTA_MINUTES, minutes).apply();
    }

    public boolean isCurfewEnabled() {
        return prefs.getBoolean(KEY_CURFEW_ENABLED, true);
    }

    public void setCurfewEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_CURFEW_ENABLED, enabled).apply();
    }

    public int getCurfewStartHour() {
        return prefs.getInt(KEY_CURFEW_START_HOUR, 21); // 21:00
    }

    public int getCurfewEndHour() {
        return prefs.getInt(KEY_CURFEW_END_HOUR, 6);   // 06:00
    }

    public void setCurfewHours(int startHour, int endHour) {
        prefs.edit()
                .putInt(KEY_CURFEW_START_HOUR, startHour)
                .putInt(KEY_CURFEW_END_HOUR, endHour)
                .apply();
    }

    public boolean isCurfewActiveNow() {
        if (!isCurfewEnabled()) return false;
        Calendar c = Calendar.getInstance();
        int hour = c.get(Calendar.HOUR_OF_DAY);
        int start = getCurfewStartHour();
        int end = getCurfewEndHour();
        if (start > end) {
            // Over midnight, e.g. 21 to 6
            return hour >= start || hour < end;
        } else {
            return hour >= start && hour < end;
        }
    }

    public int getBonusMinutesToday() {
        String today = getTodayDateKey();
        String savedDate = prefs.getString(KEY_BONUS_DATE, "");
        if (today.equals(savedDate)) {
            return prefs.getInt(KEY_BONUS_MINUTES, 0);
        }
        return 0;
    }

    public void addBonusMinutes(int minutes) {
        int current = getBonusMinutesToday();
        prefs.edit()
                .putString(KEY_BONUS_DATE, getTodayDateKey())
                .putInt(KEY_BONUS_MINUTES, current + minutes)
                .apply();
    }

    public int getTotalAllowedMinutesToday() {
        int quota = getDailyQuotaMinutes();
        if (quota <= 0) return 0; // 0 = unlimited
        return quota + getBonusMinutesToday();
    }

    public boolean isQuotaExpired(long usedMinutesToday) {
        int allowed = getTotalAllowedMinutesToday();
        if (allowed <= 0) return false; // unlimited
        return usedMinutesToday >= allowed;
    }

    public int getRemainingMinutes(long usedMinutesToday) {
        int allowed = getTotalAllowedMinutesToday();
        if (allowed <= 0) return 9999;
        return Math.max(0, allowed - (int) usedMinutesToday);
    }

    public boolean shouldShow5MinWarning(long usedMinutesToday) {
        int remaining = getRemainingMinutes(usedMinutesToday);
        if (remaining <= 5 && remaining > 0) {
            String today = getTodayDateKey();
            String warnedDate = prefs.getString(KEY_WARNING_SHOWN_DATE, "");
            if (!today.equals(warnedDate)) {
                prefs.edit().putString(KEY_WARNING_SHOWN_DATE, today).apply();
                return true;
            }
        }
        return false;
    }

    private static String getTodayDateKey() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.YEAR) + "-" + (c.get(Calendar.MONTH) + 1) + "-" + c.get(Calendar.DAY_OF_MONTH);
    }
}
