package com.next.tv.launcher.rec;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.next.tv.launcher.TvRecommendationReader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 2-Tier Recommendation Cache: Persistent Disk Cache + Seed Asset Cache.
 * Guarantees Home screen recommendations never render empty on data clear or slow EPG startup.
 */
public final class RecommendationCacheManager {
    private static final String TAG = "TvHome.RecCache";
    private static final String CACHE_FILE_NAME = "recommendations_cache.json";
    private static final String ASSET_SEED_FILE = "default_recommendations.json";

    public static synchronized void saveToDisk(Context context, List<TvRecommendationReader.MovieSection> sections) {
        if (context == null || sections == null || sections.isEmpty()) return;

        try {
            JSONArray arr = new JSONArray();
            for (TvRecommendationReader.MovieSection sec : sections) {
                if (sec == null || sec.items == null || sec.items.isEmpty()) continue;
                JSONObject sObj = new JSONObject();
                sObj.put("id", sec.id);
                sObj.put("title", sec.title);
                sObj.put("isPortrait", sec.isPortrait);

                JSONArray itemsArr = new JSONArray();
                for (TvRecommendationReader.MovieItem item : sec.items) {
                    if (item == null) continue;
                    JSONObject iObj = new JSONObject();
                    iObj.put("id", item.id);
                    iObj.put("title", item.title != null ? item.title : "");
                    iObj.put("episodeTitle", item.episodeTitle != null ? item.episodeTitle : "");
                    iObj.put("season", item.season);
                    iObj.put("episode", item.episode);
                    iObj.put("posterArtUri", item.posterArtUri != null ? item.posterArtUri.toString() : "");
                    iObj.put("intentUriString", item.intentUriString != null ? item.intentUriString : "");
                    iObj.put("durationMs", item.durationMs);
                    iObj.put("positionMs", item.positionMs);
                    iObj.put("progressPercent", item.progressPercent);
                    iObj.put("isMovie", item.isMovie);
                    iObj.put("aspectRatio", item.aspectRatio);
                    iObj.put("packageName", item.packageName != null ? item.packageName : "");
                    itemsArr.put(iObj);
                }
                sObj.put("items", itemsArr);
                arr.put(sObj);
            }

            File file = new File(context.getFilesDir(), CACHE_FILE_NAME);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(arr.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to save recommendations cache: " + e.getMessage());
        }
    }

    public static synchronized List<TvRecommendationReader.MovieSection> loadCached(Context context) {
        if (context == null) return new ArrayList<>();
        List<TvRecommendationReader.MovieSection> result = loadFromDisk(context);
        if (result != null && !result.isEmpty()) {
            return result;
        }
        return loadFromAssets(context);
    }

    private static List<TvRecommendationReader.MovieSection> loadFromAssets(Context context) {
        try (InputStream is = context.getAssets().open(ASSET_SEED_FILE)) {
            String json = readStreamToString(is);
            return parseJson(json);
        } catch (Exception e) {
            Log.w(TAG, "Failed to read asset recommendations seed: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private static List<TvRecommendationReader.MovieSection> loadFromDisk(Context context) {
        File file = new File(context.getFilesDir(), CACHE_FILE_NAME);
        if (!file.exists() || file.length() == 0) return null;

        try (FileInputStream fis = new FileInputStream(file)) {
            String json = readStreamToString(fis);
            return parseJson(json);
        } catch (Exception e) {
            Log.w(TAG, "Failed to read disk recommendations cache: " + e.getMessage());
            return null;
        }
    }

    private static String readStreamToString(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static List<TvRecommendationReader.MovieSection> parseJson(String json) {
        List<TvRecommendationReader.MovieSection> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return list;

        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject sObj = arr.getJSONObject(i);
                String id = sObj.optString("id", "sec_" + i);
                String title = sObj.optString("title", "");
                boolean isPortrait = sObj.optBoolean("isPortrait", false);

                JSONArray itemsArr = sObj.optJSONArray("items");
                List<TvRecommendationReader.MovieItem> items = new ArrayList<>();
                if (itemsArr != null) {
                    for (int j = 0; j < itemsArr.length(); j++) {
                        JSONObject iObj = itemsArr.getJSONObject(j);
                        long itemId = iObj.optLong("id", j);
                        String itemTitle = iObj.optString("title", "");
                        String epTitle = iObj.optString("episodeTitle", "");
                        int season = iObj.optInt("season", 0);
                        int episode = iObj.optInt("episode", 0);
                        String posterUriStr = iObj.optString("posterArtUri", "");
                        Uri posterUri = !posterUriStr.isEmpty() ? Uri.parse(posterUriStr) : null;
                        String intentUriStr = iObj.optString("intentUriString", "");
                        int duration = iObj.optInt("durationMs", 0);
                        int pos = iObj.optInt("positionMs", 0);
                        int progress = iObj.optInt("progressPercent", 0);
                        boolean isMovie = iObj.optBoolean("isMovie", false);
                        int aspect = iObj.optInt("aspectRatio", 0);
                        String pkg = iObj.optString("packageName", "");

                        items.add(new TvRecommendationReader.MovieItem(
                                itemId, itemTitle, epTitle, season, episode,
                                posterUri, intentUriStr, duration, pos, progress,
                                isMovie, aspect, pkg
                        ));
                    }
                }

                if (!items.isEmpty()) {
                    list.add(new TvRecommendationReader.MovieSection(id, title, items, isPortrait));
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse recommendations JSON: " + e.getMessage());
        }
        return list;
    }
}
