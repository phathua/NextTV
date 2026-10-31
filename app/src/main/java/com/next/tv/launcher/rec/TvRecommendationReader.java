package com.next.tv.launcher.rec;

import com.next.tv.launcher.util.FastPosterLoader;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads recommendation programs from Android TV Leanback channels and WatchNext (NuvioTV, Stremio, etc.).
 */
public final class TvRecommendationReader {
    private static final String TAG = "TvHome.RecReader";
    public static final String NUVIO_PACKAGE = "com.nuvio.tv";
    public static final String STREMIO_PACKAGE = "com.stremio.one";

    public static final String[] EPG_PACKAGES = {
            "com.stremio.one",
            "com.nuvio.tv",
            "com.netflix.ninja",
            "com.teamsmart.videomanager.tv",
            "com.google.android.youtube.tv",
            "vn.vtv.vtvgotv",
            "vn.mytvnet.b2cott",
            "com.kt.apps.media.xemtv.beta",
            "com.viettel.tv360.tv",
            "io.vimai.sctvonline.androidtv"
    };

    public static void wakeUpEpgServices(Context context) {
        if (context == null) return;
        android.content.pm.PackageManager pm = context.getPackageManager();
        for (String pkg : EPG_PACKAGES) {
            try {
                android.content.Intent initIntent = new android.content.Intent("android.media.tv.action.INITIALIZE_PROGRAMS");
                initIntent.setPackage(pkg);
                initIntent.addFlags(android.content.Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                context.sendBroadcast(initIntent);
            } catch (Exception ignored) {}
        }
        if (pm != null) {
            try {
                android.content.Intent queryIntent = new android.content.Intent("android.media.tv.action.INITIALIZE_PROGRAMS");
                java.util.List<android.content.pm.ResolveInfo> receivers = pm.queryBroadcastReceivers(queryIntent, 0);
                if (receivers != null) {
                    for (android.content.pm.ResolveInfo ri : receivers) {
                        if (ri.activityInfo != null && ri.activityInfo.packageName != null) {
                            String pkg = ri.activityInfo.packageName;
                            if (!pkg.equals(context.getPackageName())) {
                                android.content.Intent initIntent = new android.content.Intent("android.media.tv.action.INITIALIZE_PROGRAMS");
                                initIntent.setPackage(pkg);
                                initIntent.addFlags(android.content.Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                                context.sendBroadcast(initIntent);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    public static final class Contract {
        public static final Uri WATCH_NEXT_CONTENT_URI = Uri.parse("content://android.media.tv/watch_next_program");
        public static final Uri PREVIEW_PROGRAMS_CONTENT_URI = Uri.parse("content://android.media.tv/preview_program");
        public static final Uri CHANNELS_CONTENT_URI = Uri.parse("content://android.media.tv/channel");

        public static final String COLUMN_ID = "_id";
        public static final String COLUMN_CHANNEL_ID = "channel_id";
        public static final String COLUMN_TITLE = "title";
        public static final String COLUMN_DISPLAY_NAME = "display_name";
        public static final String COLUMN_POSTER_ART_URI = "poster_art_uri";
        public static final String COLUMN_INTENT_URI = "intent_uri";
        public static final String COLUMN_APP_LINK_INTENT_URI = "app_link_intent_uri";
        public static final String COLUMN_DURATION_MILLIS = "duration_millis";
        public static final String COLUMN_LAST_PLAYBACK_POSITION_MILLIS = "last_playback_position_millis";
        public static final String COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS = "last_engagement_time_utc_millis";
        public static final String COLUMN_TYPE = "type";
        public static final String COLUMN_SEASON_DISPLAY_NUMBER = "season_display_number";
        public static final String COLUMN_EPISODE_DISPLAY_NUMBER = "episode_display_number";
        public static final String COLUMN_EPISODE_TITLE = "episode_title";
        public static final String COLUMN_POSTER_ART_ASPECT_RATIO = "poster_art_aspect_ratio";
        public static final String COLUMN_PACKAGE_NAME = "package_name";
        public static final String COLUMN_WEIGHT = "weight";

        public static final int ASPECT_RATIO_16_9 = 0;
        public static final int ASPECT_RATIO_3_2 = 1;
        public static final int ASPECT_RATIO_4_3 = 2;
        public static final int ASPECT_RATIO_1_1 = 3;
        public static final int ASPECT_RATIO_2_3 = 4;

        public static final int TYPE_MOVIE = 0;
    }

    public static final class MovieItem {
        public final long id;
        public final String title;
        public final String episodeTitle;
        public final int season;
        public final int episode;
        public final Uri posterArtUri;
        public final String intentUriString;
        public final int durationMs;
        public final int positionMs;
        public final int progressPercent;
        public final boolean isMovie;
        public final int aspectRatio;
        public final String packageName;

        public MovieItem(long id, String title, String episodeTitle, int season, int episode,
                         Uri posterArtUri, String intentUriString, int durationMs, int positionMs,
                         int progressPercent, boolean isMovie, int aspectRatio, String packageName) {
            this.id = id;
            this.title = title != null ? title : "";
            this.episodeTitle = episodeTitle;
            this.season = season;
            this.episode = episode;
            this.posterArtUri = posterArtUri;
            this.intentUriString = intentUriString;
            this.durationMs = durationMs;
            this.positionMs = positionMs;
            this.progressPercent = progressPercent;
            this.isMovie = isMovie;
            this.aspectRatio = aspectRatio;
            this.packageName = packageName != null ? packageName : "";
        }

        public String getSourceName() {
            String p = packageName.toLowerCase();
            if (p.contains("nuvio")) return "Nuvio";
            if (p.contains("stremio")) return "Stremio";
            if (p.contains("netflix")) return "Netflix";
            if (p.contains("youtube") || p.contains("smarttube")) return "YouTube";
            return "";
        }

        public String getEpisodeBadgeText() {
            if (season > 0 && episode > 0) {
                return "S" + season + "E" + episode;
            } else if (episode > 0) {
                return "Tập " + episode;
            } else if (season > 0) {
                return "Mùa " + season;
            }
            return null;
        }

        public String getCleanEpisodeTitle() {
            if (episodeTitle != null && !episodeTitle.trim().isEmpty()) {
                String t = episodeTitle.trim();
                if (!t.equalsIgnoreCase(title) && !t.matches("(?i)^(S\\d+E\\d+|Tập\\s*\\d+|EP\\.?\\s*\\d+).*")) {
                    return t;
                }
            }
            return null;
        }

        public boolean isPortrait() {
            if (aspectRatio == Contract.ASPECT_RATIO_2_3 || aspectRatio == 4) {
                return true;
            }
            if (packageName != null && packageName.toLowerCase().contains("stremio")) {
                return true;
            }
            if (posterArtUri != null) {
                Boolean cached = FastPosterLoader.getInstance().isUrlPortrait(posterArtUri.toString());
                if (cached != null) return cached;
            }
            return false;
        }

        public String getSubtitle() {
            // Strictly clean subtitle: only return clean episode title if present, never repeat app name
            return getCleanEpisodeTitle();
        }
    }

    public static final class MovieSection {
        public final String id;
        public final String title;
        public final List<MovieItem> items;
        public final boolean isPortrait;

        public MovieSection(String id, String title, List<MovieItem> items, boolean isPortrait) {
            this.id = id;
            this.title = title;
            this.items = items;
            this.isPortrait = isPortrait;
        }

        public static boolean determineIsPortrait(List<MovieItem> items, String secId, String pkg) {
            if (secId != null && secId.startsWith("watch_next")) {
                return false; // WatchNext row is always landscape 16:9
            }
            if (pkg != null && pkg.toLowerCase().contains("stremio")) {
                return true; // Stremio channel provides 2:3 vertical posters
            }
            if (items == null || items.isEmpty()) return false;
            int count = 0;
            for (MovieItem m : items) {
                if (m.isPortrait()) count++;
            }
            return count > 0 && (count >= items.size() / 2);
        }
    }

    public static String normalizeChannelKey(String title) {
        if (title == null) return "";
        String clean = title.trim().toLowerCase();
        // Only split on " - " when separating program subtitle (e.g. "VTV1 - Thời sự"),
        // but preserve branded channel names like "HTV2 - Vie Channel"
        if (clean.contains(" - ") && !clean.contains("vie channel")) {
            clean = clean.split(" - ")[0].trim();
        } else if (clean.contains(" : ")) {
            clean = clean.split(" : ")[0].trim();
        }
        // Normalize Vietnamese diacritics for THVL / Vinh Long mapping
        clean = clean.replaceAll("[àáạảãâầấậẩẫăằắặẳẵ]", "a")
                     .replaceAll("[èéẹẻẽêềếệểễ]", "e")
                     .replaceAll("[ìíịỉĩ]", "i")
                     .replaceAll("[òóọỏõôồốộổỗơờớợởỡ]", "o")
                     .replaceAll("[ùúụủũưừứựửữ]", "u")
                     .replaceAll("[ỳýỵỷỹ]", "y")
                     .replaceAll("[đ]", "d");

        clean = clean.replaceAll("\\b(hd|fhd|4k|sd|kênh|kenh|channel)\\b", "")
                     .replaceAll("[\\s\\-_.]+", "");

        // Canonical alias mappings for Vietnamese TV channels
        if (clean.equals("vinhlong1") || clean.equals("thvl1hd") || clean.equals("thvl1")) {
            return "thvl1";
        } else if (clean.equals("vinhlong2") || clean.equals("thvl2hd") || clean.equals("thvl2")) {
            return "thvl2";
        } else if (clean.equals("vinhlong3") || clean.equals("thvl3hd") || clean.equals("thvl3")) {
            return "thvl3";
        } else if (clean.equals("vinhlong4") || clean.equals("thvl4hd") || clean.equals("thvl4")) {
            return "thvl4";
        }

        return clean;
    }

    /**
     * Query all available recommendation sections with per-app grouping and deduplication.
     */
    public static List<MovieSection> queryMovieSections(Context context) {
        List<MovieSection> sections = new ArrayList<>();
        Set<String> seenMovieKeys = new HashSet<>();
        Set<String> liveTvNormalizedKeys = new HashSet<>();

        try {
            // 1. Query WatchNext programs and combine all apps (Nuvio, Stremio, Cloudstream...) into one unified row
            List<MovieItem> rawWatchNext = queryWatchNext(context);
            Log.e(TAG, "queryMovieSections: rawWatchNext.size=" + rawWatchNext.size());
            List<MovieItem> watchNextItems = new ArrayList<>();

            for (MovieItem m : rawWatchNext) {
                String key = m.title.trim().toLowerCase();
                if (key.isEmpty() || !seenMovieKeys.add(key)) continue;
                watchNextItems.add(m);
            }

            if (!watchNextItems.isEmpty()) {
                sections.add(new MovieSection("watch_next_unified", "Tiếp tục xem", watchNextItems, false));
            }

            // 2. Query dedicated Live TV channels
            MovieSection liveTvSection = LiveTvChannelProvider.getLiveTvSection(context);
            Log.e(TAG, "queryMovieSections: liveTvSection.items=" + (liveTvSection != null ? liveTvSection.items.size() : 0));
            if (liveTvSection != null && !liveTvSection.items.isEmpty()) {
                sections.add(liveTvSection);
                for (MovieItem item : liveTvSection.items) {
                    if (item.title != null) {
                        String raw = item.title.trim().toLowerCase();
                        seenMovieKeys.add(raw);
                        String norm = normalizeChannelKey(item.title);
                        if (!norm.isEmpty()) {
                            liveTvNormalizedKeys.add(norm);
                            seenMovieKeys.add(norm);
                        }
                    }
                }
            }

            // 3. Query preview channels (recommendations)
            List<MovieSection> channelSections = queryPreviewChannels(context);
            Log.e(TAG, "queryMovieSections: channelSections.size=" + channelSections.size());
            for (MovieSection s : channelSections) {
                String sectionTitleLower = s.title != null ? s.title.trim().toLowerCase() : "";
                boolean isLiveTvSection = sectionTitleLower.contains("truyền hình")
                        || sectionTitleLower.contains("live tv")
                        || sectionTitleLower.contains("kênh tivi");

                List<MovieItem> cleanItems = new ArrayList<>();
                int matchedLiveTvCount = 0;
                for (MovieItem m : s.items) {
                    String key = m.title.trim().toLowerCase();
                    String norm = normalizeChannelKey(m.title);

                    if (liveTvNormalizedKeys.contains(norm) || (!norm.isEmpty() && seenMovieKeys.contains(norm)) || seenMovieKeys.contains(key)) {
                        matchedLiveTvCount++;
                        continue;
                    }
                    if (!key.isEmpty() && seenMovieKeys.add(key)) {
                        if (!norm.isEmpty()) seenMovieKeys.add(norm);
                        cleanItems.add(m);
                    }
                }

                // If this is a third-party Live TV row that strongly duplicates static Live TV, skip
                if (liveTvSection != null && !liveTvSection.items.isEmpty()) {
                    if (isLiveTvSection && matchedLiveTvCount >= 2) {
                        continue;
                    }
                } else if (isLiveTvSection && (cleanItems.isEmpty() || matchedLiveTvCount >= 2)) {
                    continue;
                }
                if (cleanItems.isEmpty()) continue;

                String channelSource = !cleanItems.isEmpty() ? cleanItems.get(0).getSourceName() : "";
                String displayTitle = s.title != null ? s.title.trim() : "";
                String lower = displayTitle.toLowerCase();

                if (lower.contains("tiếp tục xem") || lower.contains("continue watching")) {
                    displayTitle = channelSource.isEmpty() ? "Đề xuất" : "Đề xuất · " + channelSource;
                } else if (displayTitle.isEmpty() || lower.equals("kênh đề xuất") || lower.equals("đề xuất")) {
                    displayTitle = channelSource.isEmpty() ? "Đề xuất" : "Đề xuất · " + channelSource;
                } else if (!channelSource.isEmpty() && !lower.contains(channelSource.toLowerCase())) {
                    displayTitle = displayTitle + " · " + channelSource;
                }
                boolean isPortrait = MovieSection.determineIsPortrait(cleanItems, s.id, channelSource);
                sections.add(new MovieSection(s.id, displayTitle, cleanItems, isPortrait));
            }
        } catch (Exception e) {
            Log.e(TAG, "Lỗi đọc TvProvider (queryMovieSections)", e);
        }

        // If TvProvider returned sparse sections (e.g. only 1 Live TV row or empty before apps initialize), merge cached sections
        if (sections.size() <= 1) {
            List<MovieSection> cached = com.next.tv.launcher.rec.RecommendationCacheManager.loadCached(context);
            if (cached != null && !cached.isEmpty()) {
                Set<String> existingIds = new HashSet<>();
                for (MovieSection s : sections) {
                    if (s.id != null) existingIds.add(s.id);
                }
                for (MovieSection cs : cached) {
                    if (cs != null && !existingIds.contains(cs.id)) {
                        sections.add(cs);
                        if (cs.id != null) existingIds.add(cs.id);
                    }
                }
            }
        }
        // Only save when we have 2 or more sections to avoid blowing away rich cache
        if (sections.size() >= 2) {
            com.next.tv.launcher.rec.RecommendationCacheManager.saveToDisk(context, sections);
        }

        return sections;
    }

    private static List<MovieItem> queryWatchNext(Context context) {
        List<MovieItem> items = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    Contract.WATCH_NEXT_CONTENT_URI,
                    null,
                    null,
                    null,
                    Contract.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS + " DESC"
            );
            Log.e(TAG, "queryWatchNext: cursor=" + (cursor == null ? "null" : cursor.getCount()));

            if (cursor != null) {
                int idIdx = cursor.getColumnIndex(Contract.COLUMN_ID);
                int titleIdx = cursor.getColumnIndex(Contract.COLUMN_TITLE);
                int posterIdx = cursor.getColumnIndex(Contract.COLUMN_POSTER_ART_URI);
                int intentIdx = cursor.getColumnIndex(Contract.COLUMN_INTENT_URI);
                int durIdx = cursor.getColumnIndex(Contract.COLUMN_DURATION_MILLIS);
                int posIdx = cursor.getColumnIndex(Contract.COLUMN_LAST_PLAYBACK_POSITION_MILLIS);
                int typeIdx = cursor.getColumnIndex(Contract.COLUMN_TYPE);
                int seasonIdx = cursor.getColumnIndex(Contract.COLUMN_SEASON_DISPLAY_NUMBER);
                int epIdx = cursor.getColumnIndex(Contract.COLUMN_EPISODE_DISPLAY_NUMBER);
                int epTitleIdx = cursor.getColumnIndex(Contract.COLUMN_EPISODE_TITLE);
                int ratioIdx = cursor.getColumnIndex(Contract.COLUMN_POSTER_ART_ASPECT_RATIO);
                int pkgIdx = cursor.getColumnIndex(Contract.COLUMN_PACKAGE_NAME);

                while (cursor.moveToNext()) {
                    long id = idIdx >= 0 ? cursor.getLong(idIdx) : 0;
                    String title = titleIdx >= 0 ? cursor.getString(titleIdx) : "";
                    String posterUriStr = posterIdx >= 0 ? cursor.getString(posterIdx) : null;
                    String intentUri = intentIdx >= 0 ? cursor.getString(intentIdx) : null;
                    int dur = durIdx >= 0 ? cursor.getInt(durIdx) : 0;
                    int pos = posIdx >= 0 ? cursor.getInt(posIdx) : 0;
                    int progType = typeIdx >= 0 ? cursor.getInt(typeIdx) : 0;
                    int season = seasonIdx >= 0 ? cursor.getInt(seasonIdx) : 0;
                    int episode = epIdx >= 0 ? cursor.getInt(epIdx) : 0;
                    String epTitle = epTitleIdx >= 0 ? cursor.getString(epTitleIdx) : null;
                    int ratio = ratioIdx >= 0 ? cursor.getInt(ratioIdx) : Contract.ASPECT_RATIO_16_9;
                    String pkg = pkgIdx >= 0 ? cursor.getString(pkgIdx) : "";

                    int pct = 0;
                    if (dur > 0 && pos > 0) {
                        pct = Math.min(100, (int) ((pos * 100L) / dur));
                    }

                    boolean isMovie = progType == Contract.TYPE_MOVIE;
                    Uri posterArtUri = posterUriStr != null ? Uri.parse(posterUriStr) : null;

                    items.add(new MovieItem(id, title, epTitle, season, episode, posterArtUri,
                            intentUri, dur, pos, pct, isMovie, ratio, pkg));
                }
            }
        } catch (SecurityException se) {
            Log.e(TAG, "Lỗi đọc TvProvider (WatchNext SecurityException)", se);
        } catch (Exception e) {
            Log.e(TAG, "Lỗi đọc TvProvider (WatchNext)", e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return items;
    }

    private static List<MovieSection> queryPreviewChannels(Context context) {
        List<MovieSection> sections = new ArrayList<>();
        Map<Long, List<MovieItem>> programsMap = queryAllPreviewPrograms(context);
        Log.e(TAG, "queryPreviewChannels: programsMap.channelsWithPrograms=" + programsMap.size());

        Cursor channelCursor = null;
        try {
            channelCursor = context.getContentResolver().query(
                    Contract.CHANNELS_CONTENT_URI,
                    null,
                    null,
                    null,
                    null
            );
            Log.e(TAG, "queryPreviewChannels: channelCursor=" + (channelCursor == null ? "null" : channelCursor.getCount()));

            if (channelCursor != null) {
                int idIdx = channelCursor.getColumnIndex(Contract.COLUMN_ID);
                int nameIdx = channelCursor.getColumnIndex(Contract.COLUMN_DISPLAY_NAME);
                int pkgIdx = channelCursor.getColumnIndex(Contract.COLUMN_PACKAGE_NAME);

                while (channelCursor.moveToNext()) {
                    long channelId = channelCursor.getLong(idIdx);
                    String channelName = nameIdx >= 0 ? channelCursor.getString(nameIdx) : "Đề xuất";
                    String pkgName = pkgIdx >= 0 ? channelCursor.getString(pkgIdx) : "";

                    List<MovieItem> programs = programsMap.get(channelId);
                    if (programs != null && !programs.isEmpty()) {
                        boolean isPortrait = MovieSection.determineIsPortrait(programs, "channel_" + channelId, pkgName);
                        sections.add(new MovieSection("channel_" + channelId, channelName, programs, isPortrait));
                    }
                }
            }
        } catch (SecurityException se) {
            Log.e(TAG, "Lỗi đọc TvProvider (Channels SecurityException)", se);
        } catch (Exception e) {
            Log.e(TAG, "Lỗi đọc TvProvider (Channels)", e);
        } finally {
            if (channelCursor != null) channelCursor.close();
        }
        return sections;
    }

    private static Map<Long, List<MovieItem>> queryAllPreviewPrograms(Context context) {
        Map<Long, List<MovieItem>> map = new LinkedHashMap<>();
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    Contract.PREVIEW_PROGRAMS_CONTENT_URI,
                    null,
                    null,
                    null,
                    Contract.COLUMN_WEIGHT + " DESC"
            );
            Log.e(TAG, "queryAllPreviewPrograms: cursor=" + (cursor == null ? "null" : cursor.getCount()));

            if (cursor != null) {
                int idIdx = cursor.getColumnIndex(Contract.COLUMN_ID);
                int chIdx = cursor.getColumnIndex(Contract.COLUMN_CHANNEL_ID);
                int titleIdx = cursor.getColumnIndex(Contract.COLUMN_TITLE);
                int posterIdx = cursor.getColumnIndex(Contract.COLUMN_POSTER_ART_URI);
                int intentIdx = cursor.getColumnIndex(Contract.COLUMN_INTENT_URI);
                int durIdx = cursor.getColumnIndex(Contract.COLUMN_DURATION_MILLIS);
                int posIdx = cursor.getColumnIndex(Contract.COLUMN_LAST_PLAYBACK_POSITION_MILLIS);
                int typeIdx = cursor.getColumnIndex(Contract.COLUMN_TYPE);
                int seasonIdx = cursor.getColumnIndex(Contract.COLUMN_SEASON_DISPLAY_NUMBER);
                int epIdx = cursor.getColumnIndex(Contract.COLUMN_EPISODE_DISPLAY_NUMBER);
                int epTitleIdx = cursor.getColumnIndex(Contract.COLUMN_EPISODE_TITLE);
                int ratioIdx = cursor.getColumnIndex(Contract.COLUMN_POSTER_ART_ASPECT_RATIO);
                int pkgIdx = cursor.getColumnIndex(Contract.COLUMN_PACKAGE_NAME);

                while (cursor.moveToNext()) {
                    long channelId = chIdx >= 0 ? cursor.getLong(chIdx) : -1;
                    if (channelId < 0) continue;

                    long id = idIdx >= 0 ? cursor.getLong(idIdx) : 0;
                    String title = titleIdx >= 0 ? cursor.getString(titleIdx) : "";
                    String posterUriStr = posterIdx >= 0 ? cursor.getString(posterIdx) : null;
                    String intentUri = intentIdx >= 0 ? cursor.getString(intentIdx) : null;
                    int dur = durIdx >= 0 ? cursor.getInt(durIdx) : 0;
                    int pos = posIdx >= 0 ? cursor.getInt(posIdx) : 0;
                    int progType = typeIdx >= 0 ? cursor.getInt(typeIdx) : 0;
                    int season = seasonIdx >= 0 ? cursor.getInt(seasonIdx) : 0;
                    int episode = epIdx >= 0 ? cursor.getInt(epIdx) : 0;
                    String epTitle = epTitleIdx >= 0 ? cursor.getString(epTitleIdx) : null;
                    int ratio = ratioIdx >= 0 ? cursor.getInt(ratioIdx) : Contract.ASPECT_RATIO_16_9;
                    String pkg = pkgIdx >= 0 ? cursor.getString(pkgIdx) : "";

                    int pct = 0;
                    if (dur > 0 && pos > 0) {
                        pct = Math.min(100, (int) ((pos * 100L) / dur));
                    }

                    boolean isMovie = progType == Contract.TYPE_MOVIE;
                    Uri posterArtUri = posterUriStr != null ? Uri.parse(posterUriStr) : null;

                    List<MovieItem> list = map.get(channelId);
                    if (list == null) {
                        list = new ArrayList<>();
                        map.put(channelId, list);
                    }
                    list.add(new MovieItem(id, title, epTitle, season, episode, posterArtUri,
                            intentUri, dur, pos, pct, isMovie, ratio, pkg));
                }
            }
        } catch (SecurityException se) {
            Log.e(TAG, "Lỗi đọc TvProvider (PreviewPrograms SecurityException)", se);
        } catch (Exception e) {
            Log.e(TAG, "Lỗi đọc TvProvider (PreviewPrograms)", e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return map;
    }
}
