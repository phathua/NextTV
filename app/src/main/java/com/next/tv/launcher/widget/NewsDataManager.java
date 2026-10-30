package com.next.tv.launcher.widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Manages cached news headlines from the NWP-1 Batch Protocol.
 * Client-side RSS parsing has been offloaded to Cloudflare Edge Worker (fetchRssNews),
 * keeping client TV runtime lightweight and zero-slop.
 */
public final class NewsDataManager {
    private static final String TAG = "TvHome.News";
    private static final String PREFS_NAME = "widget_news_prefs";
    private static final String KEY_CACHE_NEWS = "news_json_array";
    private static final String KEY_CACHE_TIMESTAMP = "news_timestamp";

    public static final class NewsItem {
        public final String title;
        public final String link;
        public final String imageUrl;
        public final String pubDate;
        public final String source;
        public final String category;

        public NewsItem(String title, String link, String imageUrl, String pubDate, String source, String category) {
            this.title = title;
            this.link = link;
            this.imageUrl = imageUrl;
            this.pubDate = pubDate;
            this.source = (source != null && !source.isEmpty()) ? source : "Tin mới";
            this.category = (category != null && !category.isEmpty()) ? category : "Tin tức";
        }

        public NewsItem(String title, String link, String imageUrl, String pubDate, String source) {
            this(title, link, imageUrl, pubDate, source, "Tin tức");
        }
    }

    private final SharedPreferences prefs;

    public NewsDataManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public List<NewsItem> getCachedNews() {
        List<NewsItem> list = new ArrayList<>();
        String jsonStr = prefs.getString(KEY_CACHE_NEWS, null);
        if (jsonStr == null) return list;
        try {
            JSONArray arr = new JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                list.add(new NewsItem(
                        obj.optString("title"),
                        obj.optString("link"),
                        obj.optString("imageUrl"),
                        obj.optString("pubDate"),
                        obj.optString("source", "Tin mới"),
                        obj.optString("category", "Tin tức")
                ));
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed reading cached news: " + e.getMessage());
        }
        return list;
    }

    public void saveNewsToCache(List<NewsItem> news) {
        if (news == null || news.isEmpty()) return;
        try {
            JSONArray arr = new JSONArray();
            for (NewsItem item : news) {
                JSONObject obj = new JSONObject();
                obj.put("title", item.title);
                obj.put("link", item.link);
                obj.put("imageUrl", item.imageUrl);
                obj.put("pubDate", item.pubDate);
                obj.put("source", item.source);
                obj.put("category", item.category);
                arr.put(obj);
            }
            prefs.edit()
                    .putString(KEY_CACHE_NEWS, arr.toString())
                    .putLong(KEY_CACHE_TIMESTAMP, System.currentTimeMillis())
                    .apply();
        } catch (Exception ignored) {}
    }

    public static String formatTimeAgo(String pubDateStr) {
        if (pubDateStr == null || pubDateStr.trim().isEmpty()) {
            return "Tin mới";
        }
        String[] patterns = new String[]{
                "EEE, dd MMM yyyy HH:mm:ss Z",
                "EEE, dd MMM yyyy HH:mm:ss z",
                "yyyy-MM-dd'T'HH:mm:ssZ",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd HH:mm:ss",
                "dd/MM/yyyy HH:mm:ss"
        };
        Date date = null;
        for (String pattern : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.US);
                date = sdf.parse(pubDateStr.trim());
                if (date != null) break;
            } catch (Exception ignored) {}
        }
        if (date == null) {
            return "Tin mới";
        }
        long diff = System.currentTimeMillis() - date.getTime();
        if (diff < 60 * 1000L) {
            return "Vừa xong";
        }
        if (diff < 60 * 60 * 1000L) {
            long mins = diff / (60 * 1000L);
            return mins + " phút trước";
        }
        if (diff < 24 * 3600 * 1000L) {
            long hours = diff / (3600 * 1000L);
            return hours + " giờ trước";
        }
        if (diff < 48 * 3600 * 1000L) {
            return "Hôm qua";
        }
        if (diff < 7 * 24 * 3600 * 1000L) {
            long days = diff / (24 * 3600 * 1000L);
            return days + " ngày trước";
        }
        try {
            return new SimpleDateFormat("dd/MM", Locale.getDefault()).format(date);
        } catch (Exception e) {
            return "Gần đây";
        }
    }
}
