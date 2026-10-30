package com.next.tv.launcher.widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages cached sports scores and sport type preferences from the NWP-1 Batch Protocol.
 * ESPN scraping has been offloaded to Cloudflare Edge Worker (fetchSports),
 * eliminating redundant background network calls on Android TV.
 */
public final class FootballDataManager {
    private static final String TAG = "TvHome.Football";
    private static final String PREFS_NAME = "tvhome_football_data";
    private static final String KEY_MATCHES_CACHE = "cached_matches_json";
    private static final String KEY_CACHE_TIME = "cached_matches_timestamp";
    private static final String KEY_SPORT_TYPE = "selected_sport_type";

    public static final String SPORT_SOCCER = "soccer";
    public static final String SPORT_BASKETBALL = "basketball";
    public static final String SPORT_TENNIS = "tennis";
    public static final String SPORT_BASEBALL = "baseball";
    public static final String SPORT_NFL = "nfl";
    public static final String SPORT_F1 = "f1";
    public static final String SPORT_NHL = "nhl";

    public static final class FootballMatchItem {
        public final String matchId;
        public final String sportType;
        public final String leagueName;
        public final String homeTeamName;
        public final String homeTeamShort;
        public final String homeLogoUrl;
        public final String homeScore;
        public final String awayTeamName;
        public final String awayTeamShort;
        public final String awayLogoUrl;
        public final String awayScore;
        public final String statusState; // "in", "pre", "post"
        public final String statusDetail; // "68'", "FT", "19:30"
        public final boolean isLive;

        public FootballMatchItem(String matchId, String sportType, String leagueName,
                                 String homeTeamName, String homeTeamShort, String homeLogoUrl, String homeScore,
                                 String awayTeamName, String awayTeamShort, String awayLogoUrl, String awayScore,
                                 String statusState, String statusDetail, boolean isLive) {
            this.matchId = matchId;
            this.sportType = sportType;
            this.leagueName = leagueName;
            this.homeTeamName = homeTeamName;
            this.homeTeamShort = homeTeamShort;
            this.homeLogoUrl = homeLogoUrl;
            this.homeScore = homeScore;
            this.awayTeamName = awayTeamName;
            this.awayTeamShort = awayTeamShort;
            this.awayLogoUrl = awayLogoUrl;
            this.awayScore = awayScore;
            this.statusState = statusState;
            this.statusDetail = statusDetail;
            this.isLive = isLive;
        }

        public JSONObject toJson() {
            try {
                JSONObject o = new JSONObject();
                o.put("matchId", matchId);
                o.put("sportType", sportType);
                o.put("leagueName", leagueName);
                o.put("homeTeamName", homeTeamName);
                o.put("homeTeamShort", homeTeamShort);
                o.put("homeLogoUrl", homeLogoUrl);
                o.put("homeScore", homeScore);
                o.put("awayTeamName", awayTeamName);
                o.put("awayTeamShort", awayTeamShort);
                o.put("awayLogoUrl", awayLogoUrl);
                o.put("awayScore", awayScore);
                o.put("statusState", statusState);
                o.put("statusDetail", statusDetail);
                o.put("isLive", isLive);
                return o;
            } catch (Exception e) {
                return new JSONObject();
            }
        }

        public static FootballMatchItem fromJson(JSONObject o) {
            return new FootballMatchItem(
                    o.optString("matchId", ""),
                    o.optString("sportType", SPORT_SOCCER),
                    o.optString("leagueName", "Bóng đá"),
                    o.optString("homeTeamName", "Đội nhà"),
                    o.optString("homeTeamShort", "HOME"),
                    o.optString("homeLogoUrl", ""),
                    o.optString("homeScore", "0"),
                    o.optString("awayTeamName", "Đội khách"),
                    o.optString("awayTeamShort", "AWAY"),
                    o.optString("awayLogoUrl", ""),
                    o.optString("awayScore", "0"),
                    o.optString("statusState", "pre"),
                    o.optString("statusDetail", "VS"),
                    o.optBoolean("isLive", false)
            );
        }
    }

    private static FootballDataManager instance;
    private final SharedPreferences prefs;

    public static synchronized FootballDataManager getInstance(Context context) {
        if (instance == null) {
            instance = new FootballDataManager(context.getApplicationContext());
        }
        return instance;
    }

    private FootballDataManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getSelectedSportType() {
        return prefs.getString(KEY_SPORT_TYPE, SPORT_SOCCER);
    }

    public void setSelectedSportType(String sportType) {
        prefs.edit().putString(KEY_SPORT_TYPE, sportType).apply();
    }

    public List<FootballMatchItem> getCachedMatches() {
        List<FootballMatchItem> list = new ArrayList<>();
        String json = prefs.getString(KEY_MATCHES_CACHE, null);
        if (json == null || json.isEmpty()) return list;

        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                list.add(FootballMatchItem.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading cached sports matches: " + e.getMessage());
        }
        return list;
    }

    public void saveMatchesToCache(List<FootballMatchItem> matches) {
        if (matches == null || matches.isEmpty()) return;
        try {
            JSONArray arr = new JSONArray();
            for (FootballMatchItem item : matches) {
                arr.put(item.toJson());
            }
            prefs.edit()
                    .putString(KEY_MATCHES_CACHE, arr.toString())
                    .putLong(KEY_CACHE_TIME, System.currentTimeMillis())
                    .apply();
        } catch (Exception e) {
            Log.e(TAG, "Error caching sports matches: " + e.getMessage());
        }
    }
}
