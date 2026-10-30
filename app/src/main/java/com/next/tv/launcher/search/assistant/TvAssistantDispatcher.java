package com.next.tv.launcher.search.assistant;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance deterministic & fuzzy NLU / Intent Dispatcher for NextTV Voice Assistant.
 * 100% native Java algorithms, zero-cloud LLM, execution time < 5ms.
 * Pre-compiled patterns, diacritic normalization, space collapsing, and Vietnamese geographic coordinates.
 */
public final class TvAssistantDispatcher {

    public static final int INTENT_NONE = 0;
    public static final int INTENT_YOUTUBE_VIDEO_SEARCH = 1;
    public static final int INTENT_MOVIE_DIRECT_SEARCH = 2;
    public static final int INTENT_WEATHER = 3;
    public static final int INTENT_GOLD = 4;
    public static final int INTENT_NEWS = 5;
    public static final int INTENT_SPORTS = 6;
    public static final int INTENT_VOLUME = 7;
    public static final int INTENT_BLUETOOTH = 8;
    public static final int INTENT_APP_LAUNCH = 9;
    public static final int INTENT_MEDIA_FALLBACK = 10;

    // Volume sub-actions
    public static final int VOL_ACTION_UP = 1;
    public static final int VOL_ACTION_DOWN = 2;
    public static final int VOL_ACTION_MUTE = 3;
    public static final int VOL_ACTION_UNMUTE = 4;
    public static final int VOL_ACTION_SET = 5;

    // Bluetooth sub-actions
    public static final int BT_ACTION_ENABLE = 1;
    public static final int BT_ACTION_DISABLE = 2;
    public static final int BT_ACTION_SETTINGS = 3;

    // Pre-compiled regular expressions for microsecond execution
    private static final Pattern PATTERN_DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern PATTERN_SPACES = Pattern.compile("\\s+");
    private static final Pattern PATTERN_DIGITS = Pattern.compile("(\\d{1,3})");

    private static final Pattern PATTERN_VIDEO_PREFIX = Pattern.compile(
            "(?i)^(tìm kiếm video|tim kiem video|tìm video|tim video|xem clip|xem video|mở video|mo video|bật video|bat video|nghe nhạc|nghe nhac|nghe bài hát|nghe bai hat|tìm kiếm|tim kiem|tìm|tim|xem|nghe|video|clip)\\s+"
    );
    private static final Pattern PATTERN_VIDEO_SUFFIX = Pattern.compile(
            "(?i)\\s+(trên youtube|tren youtube|ở youtube|o youtube|qua youtube|bằng youtube|bang youtube|youtube)$"
    );

    private static final Pattern PATTERN_MOVIE_PREFIX = Pattern.compile(
            "(?i)^(tìm phim|tim phim|xem phim|mở phim|mo phim|bật phim|bat phim|phim bộ|phim lẻ|phim)\\s+"
    );
    private static final Pattern PATTERN_MOVIE_SUFFIX = Pattern.compile(
            "(?i)\\s+(trên nuvio|tren nuvio|ở nuvio|o nuvio|trên stremio|tren stremio|ở stremio|o stremio|trên cloudstream|tren cloudstream)$"
    );

    private static final Pattern PATTERN_APP_PREFIX = Pattern.compile(
            "(?i)^(mở ứng dụng|mo ung dung|mở app|mo app|mở|mo|bật|bat|vào|vao|khởi động|khoi dong|open|launch)\\s+"
    );

    // City coordinates lookup (lat, lon) for accurate real-time meteorology
    public static final class CityCoordinate {
        public final String cityName;
        public final double lat;
        public final double lon;

        public CityCoordinate(String cityName, double lat, double lon) {
            this.cityName = cityName;
            this.lat = lat;
            this.lon = lon;
        }
    }

    private static final Map<String, CityCoordinate> CITY_MAP = new HashMap<>();
    static {
        CITY_MAP.put("hà nội", new CityCoordinate("Hà Nội", 21.0285, 105.8542));
        CITY_MAP.put("tp. hồ chí minh", new CityCoordinate("TP. Hồ Chí Minh", 10.8231, 106.6297));
        CITY_MAP.put("hồ chí minh", new CityCoordinate("TP. Hồ Chí Minh", 10.8231, 106.6297));
        CITY_MAP.put("sài gòn", new CityCoordinate("TP. Hồ Chí Minh", 10.8231, 106.6297));
        CITY_MAP.put("đà nẵng", new CityCoordinate("Đà Nẵng", 16.0685, 108.2215));
        CITY_MAP.put("hải phòng", new CityCoordinate("Hải Phòng", 20.8449, 106.6881));
        CITY_MAP.put("cần thơ", new CityCoordinate("Cần Thơ", 10.0452, 105.7469));
        CITY_MAP.put("nha trang", new CityCoordinate("Nha Trang", 12.2388, 109.1967));
        CITY_MAP.put("huế", new CityCoordinate("Huế", 16.4637, 107.5909));
        CITY_MAP.put("đà lạt", new CityCoordinate("Đà Lạt", 11.9404, 108.4583));
        CITY_MAP.put("quy nhơn", new CityCoordinate("Quy Nhơn", 13.7830, 109.2197));
        CITY_MAP.put("vũng tàu", new CityCoordinate("Vũng Tàu", 10.3460, 107.0843));
        CITY_MAP.put("buôn ma thuột", new CityCoordinate("Buôn Ma Thuột", 12.6667, 108.0500));
        CITY_MAP.put("hạ long", new CityCoordinate("Hạ Long", 20.9505, 107.0734));
        CITY_MAP.put("vinh", new CityCoordinate("Vinh", 18.6734, 105.6813));
        CITY_MAP.put("phan thiết", new CityCoordinate("Phan Thiết", 10.9298, 108.0995));
    }

    public static CityCoordinate getCityCoordinate(String cityName) {
        if (cityName == null) return null;
        String clean = cityName.trim().toLowerCase(Locale.ROOT);
        return CITY_MAP.get(clean);
    }

    public static final class ParsedIntent {
        public final int type;
        public final String rawQuery;
        public final String cleanQuery;
        public final String targetCity;
        public final String targetApp;
        public final int volumeAction;
        public final int volumePercent;
        public final int bluetoothAction;

        public ParsedIntent(int type, String rawQuery, String cleanQuery, String targetCity,
                            String targetApp, int volumeAction, int volumePercent, int bluetoothAction) {
            this.type = type;
            this.rawQuery = rawQuery;
            this.cleanQuery = cleanQuery != null ? cleanQuery.trim() : "";
            this.targetCity = targetCity;
            this.targetApp = targetApp;
            this.volumeAction = volumeAction;
            this.volumePercent = volumePercent;
            this.bluetoothAction = bluetoothAction;
        }
    }

    /**
     * Strips Vietnamese diacritics, collapses multiple spaces, and converts to lowercase.
     */
    public static String stripDiacritics(String str) {
        if (str == null) return "";
        String normalized = Normalizer.normalize(str, Normalizer.Form.NFD);
        String result = PATTERN_DIACRITICS.matcher(normalized).replaceAll("");
        result = result.replace('đ', 'd').replace('Đ', 'D');
        result = PATTERN_SPACES.matcher(result).replaceAll(" ");
        return result.toLowerCase(Locale.ROOT).trim();
    }

    /**
     * Cleans target video search query by stripping command verbs and YouTube references.
     * E.g. "tìm video làm bánh trên youtube" -> "làm bánh"
     */
    public static String cleanVideoQuery(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        trimmed = PATTERN_VIDEO_PREFIX.matcher(trimmed).replaceFirst("").trim();
        trimmed = PATTERN_VIDEO_SUFFIX.matcher(trimmed).replaceFirst("").trim();
        return trimmed.isEmpty() ? raw.trim() : trimmed;
    }

    /**
     * Cleans direct movie query for Nuvio/Stremio/CloudStream.
     * E.g. "tìm phim Batman trên Nuvio" -> "Batman"
     */
    public static String cleanMovieQuery(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        trimmed = PATTERN_MOVIE_PREFIX.matcher(trimmed).replaceFirst("").trim();
        trimmed = PATTERN_MOVIE_SUFFIX.matcher(trimmed).replaceFirst("").trim();
        return trimmed.isEmpty() ? raw.trim() : trimmed;
    }

    /**
     * Dispatches input string into structured ParsedIntent.
     */
    public static ParsedIntent dispatch(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return new ParsedIntent(INTENT_NONE, "", "", null, null, 0, 0, 0);
        }

        String rawClean = raw.trim();
        String norm = stripDiacritics(rawClean);

        // 1. Volume Control Intent
        if (matchesVolume(norm)) {
            int volAction = parseVolumeAction(norm);
            int volPercent = parseVolumePercent(norm);
            return new ParsedIntent(INTENT_VOLUME, rawClean, rawClean, null, null, volAction, volPercent, 0);
        }

        // 2. Bluetooth Control Intent
        if (matchesBluetooth(norm)) {
            int btAction = parseBluetoothAction(norm);
            return new ParsedIntent(INTENT_BLUETOOTH, rawClean, rawClean, null, null, 0, 0, btAction);
        }

        // 3. YouTube Video Search Intent (Checked before general app launch)
        if (matchesYouTubeVideo(norm)) {
            String cleanQuery = cleanVideoQuery(rawClean);
            return new ParsedIntent(INTENT_YOUTUBE_VIDEO_SEARCH, rawClean, cleanQuery, null, "com.google.android.youtube.tv", 0, 0, 0);
        }

        // 4. Movie Direct Search on Target App (Nuvio / Stremio / CloudStream)
        if (matchesDirectMovie(norm)) {
            String targetApp = extractMovieApp(norm);
            String cleanMovie = cleanMovieQuery(rawClean);
            return new ParsedIntent(INTENT_MOVIE_DIRECT_SEARCH, rawClean, cleanMovie, null, targetApp, 0, 0, 0);
        }

        // 5. App Launch Intent (e.g. "mở youtube", "mở vtv go", "mở netflix", "mở cài đặt")
        if (matchesAppLaunch(norm)) {
            String appQuery = extractAppQuery(rawClean);
            return new ParsedIntent(INTENT_APP_LAUNCH, rawClean, appQuery, null, null, 0, 0, 0);
        }

        // 6. Weather Forecast Intent
        if (matchesWeather(norm)) {
            String city = extractCity(norm);
            return new ParsedIntent(INTENT_WEATHER, rawClean, rawClean, city, null, 0, 0, 0);
        }

        // 7. Gold Price Intent
        if (matchesGold(norm)) {
            return new ParsedIntent(INTENT_GOLD, rawClean, rawClean, null, null, 0, 0, 0);
        }

        // 8. News / Current Events Intent
        if (matchesNews(norm)) {
            return new ParsedIntent(INTENT_NEWS, rawClean, rawClean, null, null, 0, 0, 0);
        }

        // 9. Sports / Football Intent
        if (matchesSports(norm)) {
            return new ParsedIntent(INTENT_SPORTS, rawClean, rawClean, null, null, 0, 0, 0);
        }

        // 10. Fallback Media Search
        return new ParsedIntent(INTENT_MEDIA_FALLBACK, rawClean, rawClean, null, null, 0, 0, 0);
    }

    // -------------------------------------------------------------
    // Pattern Matchers & Helper Extractors
    // -------------------------------------------------------------

    private static boolean matchesVolume(String norm) {
        return norm.contains("tang am luong") || norm.contains("giam am luong") ||
                norm.contains("tang am") || norm.contains("giam am") ||
                norm.contains("tang tieng") || norm.contains("giam tieng") ||
                norm.contains("to len") || norm.contains("nho lai") || norm.contains("be lai") ||
                norm.contains("tat tieng") || norm.contains("tat am") || norm.contains("bat tieng") ||
                norm.contains("mo tieng") || norm.contains("im lang") || norm.contains("mute") ||
                norm.contains("volume up") || norm.contains("volume down") ||
                norm.startsWith("am luong ") || norm.contains("dat am luong");
    }

    private static int parseVolumeAction(String norm) {
        if (norm.contains("tang") || norm.contains("to len") || norm.contains("up")) {
            return VOL_ACTION_UP;
        } else if (norm.contains("giam") || norm.contains("nho lai") || norm.contains("be lai") || norm.contains("down")) {
            return VOL_ACTION_DOWN;
        } else if (norm.contains("tat tieng") || norm.contains("tat am") || norm.contains("im lang") || norm.contains("mute")) {
            return VOL_ACTION_MUTE;
        } else if (norm.contains("bat tieng") || norm.contains("mo tieng") || norm.contains("unmute")) {
            return VOL_ACTION_UNMUTE;
        } else if (norm.contains("am luong") && PATTERN_DIGITS.matcher(norm).find()) {
            return VOL_ACTION_SET;
        }
        return VOL_ACTION_UP;
    }

    private static int parseVolumePercent(String norm) {
        Matcher m = PATTERN_DIGITS.matcher(norm);
        if (m.find()) {
            try {
                int val = Integer.parseInt(m.group(1));
                return Math.max(0, Math.min(100, val));
            } catch (Exception ignored) {}
        }
        return -1;
    }

    private static boolean matchesBluetooth(String norm) {
        return norm.contains("bluetooth") || norm.contains("ket noi tai nghe");
    }

    private static int parseBluetoothAction(String norm) {
        if (norm.contains("bat bluetooth") || norm.contains("mo bluetooth") || norm.contains("turn on bluetooth")) {
            return BT_ACTION_ENABLE;
        } else if (norm.contains("tat bluetooth") || norm.contains("ngat bluetooth") || norm.contains("turn off bluetooth")) {
            return BT_ACTION_DISABLE;
        }
        return BT_ACTION_SETTINGS;
    }

    private static boolean matchesYouTubeVideo(String norm) {
        if (norm.startsWith("tim video ") || norm.startsWith("xem video ") || norm.startsWith("xem clip ") ||
                norm.startsWith("tim clip ") || norm.startsWith("nghe nhac ") || norm.startsWith("video ") ||
                norm.contains(" tren youtube") || norm.contains(" o youtube") || norm.contains(" qua youtube") ||
                norm.contains(" bang youtube")) {
            return true;
        }
        if (norm.startsWith("tim ") && (norm.contains("youtube") || norm.contains("video") || norm.contains("clip") || norm.contains("bai hat"))) {
            return true;
        }
        return false;
    }

    private static boolean matchesDirectMovie(String norm) {
        return (norm.contains("tren nuvio") || norm.contains("o nuvio") ||
                norm.contains("tren stremio") || norm.contains("o stremio") ||
                norm.contains("tren cloudstream") || norm.contains("o cloudstream"));
    }

    private static String extractMovieApp(String norm) {
        if (norm.contains("nuvio")) return "com.nuvio.tv";
        if (norm.contains("stremio")) return "com.stremio.one";
        if (norm.contains("cloudstream")) return "com.lagradost.cloudstream3";
        return "com.nuvio.tv";
    }

    private static boolean matchesAppLaunch(String norm) {
        return norm.startsWith("mo ") || norm.startsWith("bat ") ||
                norm.startsWith("vao ") || norm.startsWith("khoi dong ") ||
                norm.startsWith("open ") || norm.startsWith("launch ");
    }

    private static String extractAppQuery(String raw) {
        String clean = PATTERN_APP_PREFIX.matcher(raw).replaceFirst("").trim();
        return clean.isEmpty() ? raw.trim() : clean;
    }

    private static boolean matchesWeather(String norm) {
        return norm.contains("thoi tiet") || norm.contains("du bao thoi tiet") ||
                norm.contains("nhiet do") || norm.contains("troi co mua") ||
                norm.contains("co mua khong") || norm.contains("thoi tiet hom nay") ||
                norm.contains("thoi tiet ngay mai");
    }

    private static String extractCity(String norm) {
        if (norm.contains("ha noi")) return "Hà Nội";
        if (norm.contains("ho chi minh") || norm.contains("sai gon") || norm.contains("tphcm") || norm.contains("tp hcm")) return "TP. Hồ Chí Minh";
        if (norm.contains("da nang")) return "Đà Nẵng";
        if (norm.contains("hai phong")) return "Hải Phòng";
        if (norm.contains("can tho")) return "Cần Thơ";
        if (norm.contains("nha trang")) return "Nha Trang";
        if (norm.contains("hue")) return "Huế";
        if (norm.contains("da lat")) return "Đà Lạt";
        if (norm.contains("quy nhon")) return "Quy Nhơn";
        if (norm.contains("vung tau")) return "Vũng Tàu";
        if (norm.contains("buon ma thuot")) return "Buôn Ma Thuột";
        if (norm.contains("ha long")) return "Hạ Long";
        if (norm.contains("vinh")) return "Vinh";
        if (norm.contains("phan thiet")) return "Phan Thiết";
        return null;
    }

    private static boolean matchesGold(String norm) {
        return norm.contains("gia vang") || norm.contains("vang sjc") ||
                norm.contains("vang 9999") || norm.contains("vang rong thang long") ||
                norm.contains("gia vang hom nay") || norm.equals("vang");
    }

    private static boolean matchesNews(String norm) {
        return norm.contains("thoi su") || norm.contains("tin tuc") ||
                norm.contains("tin nong") || norm.contains("tin moi") ||
                norm.contains("tin hom nay") || norm.contains("diem tin");
    }

    private static boolean matchesSports(String norm) {
        return norm.contains("the thao") || norm.contains("ti so") ||
                norm.contains("bong da") || norm.contains("ket qua bong da") ||
                norm.contains("ngoai hang anh") || norm.contains("c1");
    }
}
