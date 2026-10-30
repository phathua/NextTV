package com.next.tv.launcher.widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * YouTube Trending Data Manager using YouTube InnerTube Web API.
 * Consumes zero Google API keys or quotas.
 * Caches trending videos in SharedPreferences for 6 hours to minimize CPU & bandwidth.
 */
public final class YouTubeTrendingManager {
    private static final String TAG = "TvHome.YouTube";
    private static final String PREFS_NAME = "widget_youtube_prefs";
    private static final String KEY_CACHE_VIDEOS = "youtube_videos_cache_v4";
    private static final String KEY_CACHE_TIMESTAMP = "youtube_timestamp";
    private static final long CACHE_DURATION_MS = 6 * 3600 * 1000L; // 6 hours

    public static final class YouTubeVideoItem {
        public final String videoId;
        public final String title;
        public final String channelTitle;
        public final String durationText;
        public final String viewCountText;
        public final String thumbnailUrl;
        public final String categoryLabel;

        public YouTubeVideoItem(String videoId, String title, String channelTitle,
                                String durationText, String viewCountText, String thumbnailUrl) {
            this(videoId, title, channelTitle, durationText, viewCountText, thumbnailUrl, "Thịnh hành");
        }

        public YouTubeVideoItem(String videoId, String title, String channelTitle,
                                String durationText, String viewCountText, String thumbnailUrl, String categoryLabel) {
            this.videoId = videoId;
            this.title = title;
            this.channelTitle = channelTitle;
            this.durationText = durationText;
            this.viewCountText = viewCountText;
            this.thumbnailUrl = thumbnailUrl;
            this.categoryLabel = categoryLabel != null ? categoryLabel : "Thịnh hành";
        }
    }

    private final SharedPreferences prefs;

    public YouTubeTrendingManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isCacheExpired() {
        long savedTime = prefs.getLong(KEY_CACHE_TIMESTAMP, 0L);
        return (System.currentTimeMillis() - savedTime) > CACHE_DURATION_MS;
    }

    public List<YouTubeVideoItem> getCachedVideos() {
        List<YouTubeVideoItem> list = new ArrayList<>();
        String jsonStr = prefs.getString(KEY_CACHE_VIDEOS, null);
        if (jsonStr == null || jsonStr.isEmpty()) return list;

        try {
            JSONArray arr = new JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                list.add(new YouTubeVideoItem(
                        obj.optString("videoId"),
                        obj.optString("title"),
                        obj.optString("channelTitle"),
                        obj.optString("durationText"),
                        obj.optString("viewCountText"),
                        obj.optString("thumbnailUrl"),
                        obj.optString("categoryLabel", "Thịnh hành")
                ));
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed reading cached videos: " + e.getMessage());
        }
        return list;
    }

    public void saveCache(List<YouTubeVideoItem> videos) {
        if (videos == null || videos.isEmpty()) return;
        try {
            JSONArray arr = new JSONArray();
            for (YouTubeVideoItem item : videos) {
                JSONObject obj = new JSONObject();
                obj.put("videoId", item.videoId);
                obj.put("title", item.title);
                obj.put("channelTitle", item.channelTitle);
                obj.put("durationText", item.durationText);
                obj.put("viewCountText", item.viewCountText);
                obj.put("thumbnailUrl", item.thumbnailUrl);
                obj.put("categoryLabel", item.categoryLabel);
                arr.put(obj);
            }
            prefs.edit()
                    .putString(KEY_CACHE_VIDEOS, arr.toString())
                    .putLong(KEY_CACHE_TIMESTAMP, System.currentTimeMillis())
                    .apply();
        } catch (Exception e) {
            Log.w(TAG, "Failed caching youtube videos: " + e.getMessage());
        }
    }

    public List<YouTubeVideoItem> fetchSync() {
        List<YouTubeVideoItem> results = fetchFromInnerTube();
        if (results != null && !results.isEmpty()) {
            saveCache(results);
            return results;
        }
        // Fallback to cached
        List<YouTubeVideoItem> cached = getCachedVideos();
        if (!cached.isEmpty()) return cached;
        return Collections.emptyList();
    }

    private static final String[][] CHANNELS = new String[][] {
        {"Âm nhạc", "VLPL4fGSI1pDJn57DkisEwlIpcs9FAt5yudJ"},
        {"Thể thao", "UCEgdi0XIXXZ-qJOFPf4JSKw"},
        {"Trò chơi", "UCOpNcN46UbXVtpKMrmU4Abg"},
        {"Tin tức", "UCYfdidRxbB8Qhf0Nx7ioOYw"},
        {"Giáo dục", "UCtFRv9O2AHqOZjjynzrv-xg"}
    };

    private List<YouTubeVideoItem> fetchFromInnerTube() {
        List<List<YouTubeVideoItem>> allCategories = new ArrayList<>();
        java.util.Set<String> seenIds = new java.util.HashSet<>();

        for (String[] cat : CHANNELS) {
            String label = cat[0];
            String browseId = cat[1];
            List<YouTubeVideoItem> catItems = fetchCategoryChannel(browseId, label, seenIds);
            if (!catItems.isEmpty()) {
                allCategories.add(catItems);
            }
        }

        if (allCategories.isEmpty()) return null;

        // Interleave (round-robin blend) items across categories
        List<YouTubeVideoItem> blended = new ArrayList<>();
        int maxLen = 0;
        for (List<YouTubeVideoItem> c : allCategories) {
            if (c.size() > maxLen) maxLen = c.size();
        }

        for (int i = 0; i < maxLen; i++) {
            for (List<YouTubeVideoItem> c : allCategories) {
                if (i < c.size()) {
                    blended.add(c.get(i));
                }
            }
        }

        return blended.isEmpty() ? null : blended;
    }

    private List<YouTubeVideoItem> fetchCategoryChannel(String browseId, String categoryLabel, java.util.Set<String> seenIds) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://www.youtube.com/youtubei/v1/browse?prettyPrint=false");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", com.next.tv.launcher.util.NetworkConstants.DEFAULT_USER_AGENT);

            JSONObject client = new JSONObject();
            client.put("clientName", "WEB");
            client.put("clientVersion", "2.20261020.01.00");
            client.put("hl", "vi");
            client.put("gl", "VN");

            JSONObject context = new JSONObject();
            context.put("client", client);

            JSONObject payload = new JSONObject();
            payload.put("context", context);
            payload.put("browseId", browseId);

            byte[] bodyBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
                os.flush();
            }

            int code = conn.getResponseCode();
            if (code != 200) {
                return Collections.emptyList();
            }

            StringBuilder sb = new StringBuilder();
            try (InputStream is = conn.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            return parseInnerTubeResponse(sb.toString(), categoryLabel, seenIds);
        } catch (Exception e) {
            return Collections.emptyList();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private List<YouTubeVideoItem> parseInnerTubeResponse(String jsonString, String categoryLabel, java.util.Set<String> seenIds) {
        List<YouTubeVideoItem> list = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(jsonString);
            findVideoRenderers(root, list, seenIds, categoryLabel, 4);
        } catch (Exception e) {
            Log.w(TAG, "Failed parsing InnerTube JSON: " + e.getMessage());
        }
        return list;
    }

    private void findVideoRenderers(Object node, List<YouTubeVideoItem> list, java.util.Set<String> seenIds, String categoryLabel, int maxPerCategory) {
        if (list.size() >= maxPerCategory) return;
        if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;

            // 1. Classic Renderers
            JSONObject vr = null;
            if (obj.has("videoRenderer")) vr = obj.optJSONObject("videoRenderer");
            else if (obj.has("gridVideoRenderer")) vr = obj.optJSONObject("gridVideoRenderer");
            else if (obj.has("compactVideoRenderer")) vr = obj.optJSONObject("compactVideoRenderer");
            else if (obj.has("playlistVideoRenderer")) vr = obj.optJSONObject("playlistVideoRenderer");

            if (vr != null) {
                String vid = vr.optString("videoId");
                if (vid != null && !vid.isEmpty() && !seenIds.contains(vid)) {
                    seenIds.add(vid);
                    YouTubeVideoItem item = parseVideoRenderer(vr, categoryLabel);
                    if (item != null) list.add(item);
                }
                return;
            }

            // 2. Modern 2024-2026 lockupViewModel
            if (obj.has("lockupViewModel")) {
                JSONObject lvm = obj.optJSONObject("lockupViewModel");
                if (lvm != null) {
                    String vid = lvm.optString("contentId");
                    if (vid != null && !vid.isEmpty() && !seenIds.contains(vid)) {
                        seenIds.add(vid);
                        YouTubeVideoItem item = parseLockupViewModel(lvm, vid, categoryLabel);
                        if (item != null) list.add(item);
                    }
                }
                return;
            }

            java.util.Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                findVideoRenderers(obj.opt(k), list, seenIds, categoryLabel, maxPerCategory);
                if (list.size() >= maxPerCategory) break;
            }
        } else if (node instanceof JSONArray) {
            JSONArray arr = (JSONArray) node;
            for (int i = 0; i < arr.length(); i++) {
                findVideoRenderers(arr.opt(i), list, seenIds, categoryLabel, maxPerCategory);
                if (list.size() >= 12) break;
            }
        }
    }

    private YouTubeVideoItem parseLockupViewModel(JSONObject lvm, String videoId, String forcedCategory) {
        try {
            JSONObject meta = lvm.optJSONObject("metadata");
            JSONObject lvmMeta = (meta != null) ? meta.optJSONObject("lockupMetadataViewModel") : null;
            String title = "";
            if (lvmMeta != null) {
                JSONObject titleObj = lvmMeta.optJSONObject("title");
                if (titleObj != null) {
                    title = titleObj.optString("content", "");
                }
            }
            if (title.isEmpty()) return null;

            String channel = "";
            String views = "";
            if (lvmMeta != null) {
                JSONObject subMeta = lvmMeta.optJSONObject("metadata");
                JSONObject contentMeta = (subMeta != null) ? subMeta.optJSONObject("contentMetadataViewModel") : null;
                if (contentMeta != null) {
                    JSONArray rows = contentMeta.optJSONArray("metadataRows");
                    if (rows != null && rows.length() > 0) {
                        JSONObject row0 = rows.optJSONObject(0);
                        if (row0 != null) {
                            JSONArray parts0 = row0.optJSONArray("metadataParts");
                            if (parts0 == null) parts0 = row0.optJSONArray("parts");
                            if (parts0 != null && parts0.length() > 0) {
                                JSONObject p0 = parts0.optJSONObject(0);
                                if (p0 != null && p0.has("text")) {
                                    channel = p0.optJSONObject("text").optString("content", "");
                                }
                                if (parts0.length() > 1) {
                                    JSONObject p1 = parts0.optJSONObject(1);
                                    if (p1 != null && p1.has("text")) {
                                        views = p1.optJSONObject("text").optString("content", "");
                                    }
                                }
                            }
                        }
                        if (views.isEmpty() && rows.length() > 1) {
                            JSONObject row1 = rows.optJSONObject(1);
                            if (row1 != null) {
                                JSONArray parts1 = row1.optJSONArray("metadataParts");
                                if (parts1 == null) parts1 = row1.optJSONArray("parts");
                                if (parts1 != null && parts1.length() > 0) {
                                    JSONObject p0 = parts1.optJSONObject(0);
                                    if (p0 != null && p0.has("text")) {
                                        views = p0.optJSONObject("text").optString("content", "");
                                    }
                                }
                            }
                        }
                    }
                }
            }

            String duration = "";
            JSONObject contentImage = lvm.optJSONObject("contentImage");
            JSONObject thumbVm = (contentImage != null) ? contentImage.optJSONObject("thumbnailViewModel") : null;
            if (thumbVm != null) {
                JSONArray overlays = thumbVm.optJSONArray("overlays");
                if (overlays != null) {
                    for (int i = 0; i < overlays.length(); i++) {
                        JSONObject ov = overlays.optJSONObject(i);
                        if (ov != null && ov.has("thumbnailBottomOverlayViewModel")) {
                            JSONObject bOverlay = ov.optJSONObject("thumbnailBottomOverlayViewModel");
                            JSONArray badges = (bOverlay != null) ? bOverlay.optJSONArray("badges") : null;
                            if (badges != null && badges.length() > 0) {
                                JSONObject badgeObj = badges.optJSONObject(0);
                                JSONObject tBadge = (badgeObj != null) ? badgeObj.optJSONObject("thumbnailBadgeViewModel") : null;
                                if (tBadge != null) {
                                    duration = tBadge.optString("text", "");
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            String thumbUrl = "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
            String category = (forcedCategory != null && !forcedCategory.isEmpty()) ? forcedCategory : determineCategory(title, channel);
            return new YouTubeVideoItem(videoId, title, channel, duration, views, thumbUrl, category);
        } catch (Exception e) {
            return null;
        }
    }

    private YouTubeVideoItem parseVideoRenderer(JSONObject vr, String forcedCategory) {
        String videoId = vr.optString("videoId");
        if (videoId == null || videoId.isEmpty()) return null;

        String title = "";
        JSONObject titleObj = vr.optJSONObject("title");
        if (titleObj != null) {
            JSONArray runs = titleObj.optJSONArray("runs");
            if (runs != null && runs.length() > 0) {
                title = runs.optJSONObject(0).optString("text", "");
            } else {
                title = titleObj.optString("simpleText", "");
            }
        }

        String channel = "";
        JSONObject ownerObj = vr.optJSONObject("ownerText");
        if (ownerObj == null) ownerObj = vr.optJSONObject("shortBylineText");
        if (ownerObj != null) {
            JSONArray runs = ownerObj.optJSONArray("runs");
            if (runs != null && runs.length() > 0) {
                channel = runs.optJSONObject(0).optString("text", "");
            }
        }

        String views = "";
        JSONObject viewsObj = vr.optJSONObject("viewCountText");
        if (viewsObj != null) {
            views = viewsObj.optString("simpleText", "");
            if (views.isEmpty()) {
                JSONArray runs = viewsObj.optJSONArray("runs");
                if (runs != null && runs.length() > 0) {
                    views = runs.optJSONObject(0).optString("text", "");
                }
            }
        }

        String duration = "";
        JSONObject lengthObj = vr.optJSONObject("lengthText");
        if (lengthObj != null) {
            duration = lengthObj.optString("simpleText", "");
        } else {
            JSONArray overlays = vr.optJSONArray("thumbnailOverlays");
            if (overlays != null && overlays.length() > 0) {
                for (int i = 0; i < overlays.length(); i++) {
                    JSONObject ov = overlays.optJSONObject(i);
                    if (ov != null && ov.has("thumbnailOverlayTimeStatusRenderer")) {
                        JSONObject status = ov.optJSONObject("thumbnailOverlayTimeStatusRenderer");
                        if (status != null && status.has("text")) {
                            duration = status.optJSONObject("text").optString("simpleText", "");
                        }
                    }
                }
            }
        }

        String thumbUrl = "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
        JSONObject thumbObj = vr.optJSONObject("thumbnail");
        if (thumbObj != null) {
            JSONArray thumbs = thumbObj.optJSONArray("thumbnails");
            if (thumbs != null && thumbs.length() > 0) {
                JSONObject best = thumbs.optJSONObject(thumbs.length() - 1);
                if (best != null && best.has("url")) {
                    thumbUrl = best.optString("url");
                }
            }
        }

        String category = determineCategory(title, channel);
        if (title.isEmpty()) return null;
        return new YouTubeVideoItem(videoId, title, channel, duration, views, thumbUrl, category);
    }

    private String determineCategory(String title, String channel) {
        String category = "Thịnh hành";
        String lowerTitle = (title != null) ? title.toLowerCase() : "";
        String lowerChannel = (channel != null) ? channel.toLowerCase() : "";
        if (lowerTitle.contains("mv") || lowerTitle.contains("nhạc") || lowerTitle.contains("music")
                || lowerTitle.contains("official video") || lowerTitle.contains("bài hát") || lowerTitle.contains("audio")
                || lowerChannel.contains("music") || lowerChannel.contains("official")) {
            category = "Âm nhạc";
        } else if (lowerTitle.contains("tin tức") || lowerTitle.contains("thời sự") || lowerTitle.contains("vtv")
                || lowerTitle.contains("bản tin") || lowerTitle.contains("báo ") || lowerChannel.contains("vtv")
                || lowerChannel.contains("truyền hình")) {
            category = "Tin tức";
        } else if (lowerTitle.contains("game") || lowerTitle.contains("gaming") || lowerTitle.contains("liên quân")
                || lowerTitle.contains("free fire") || lowerTitle.contains("pubg")) {
            category = "Trò chơi";
        } else if (lowerTitle.contains("hài") || lowerTitle.contains("show") || lowerTitle.contains("tập")
                || lowerTitle.contains("trailer") || lowerTitle.contains("review") || lowerTitle.contains("vlog")) {
            category = "Giải trí";
        }
        return category;
    }

    public List<YouTubeVideoItem> getDefaultVideos() {
        return Collections.emptyList();
    }
}
