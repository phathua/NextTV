package com.next.tv.launcher.widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages weather data, location configuration, and 7-day meteorological forecasts via Open-Meteo.
 * Supports persistent location selection and local caching.
 */
public final class WeatherDataManager {
    private static final String TAG = "TvHome.Weather";
    private static final String PREFS_NAME = "widget_weather_prefs";

    // Basic Widget Cache
    private static final String KEY_CACHE_JSON = "weather_json";
    private static final String KEY_CACHE_TIMESTAMP = "weather_timestamp";

    // Detail Weather Cache
    private static final String KEY_CACHE_DETAIL_JSON = "weather_detail_json";
    private static final String KEY_CACHE_DETAIL_TIMESTAMP = "weather_detail_timestamp";

    // Location Settings
    public static final String KEY_LOCATION_CUSTOM = "weather_loc_custom";
    public static final String KEY_LOCATION_CITY = "weather_loc_city";
    public static final String KEY_LOCATION_LAT = "weather_loc_lat";
    public static final String KEY_LOCATION_LON = "weather_loc_lon";

    public static final String DEFAULT_CITY = "Đà Nẵng";
    public static final double DEFAULT_LAT = 16.0685;
    public static final double DEFAULT_LON = 108.2215;

    private static final long CACHE_DURATION_MS = 3 * 3600 * 1000L; // 3 hours

    // Legacy Basic Day Forecast (Preserved for compatibility)
    public static final class DayForecast {
        public final String dayName;
        public final int weatherCode;
        public final int minTemp;
        public final int maxTemp;

        public DayForecast(String dayName, int weatherCode, int minTemp, int maxTemp) {
            this.dayName = dayName;
            this.weatherCode = weatherCode;
            this.minTemp = minTemp;
            this.maxTemp = maxTemp;
        }
    }

    // Legacy Weather Info for Launcher Widget
    public static final class WeatherInfo {
        public final String city;
        public final double temperature;
        public final int humidity;
        public final String description;
        public final int weatherCode;
        public final List<DayForecast> dailyForecast;

        public WeatherInfo(String city, double temperature, int humidity, String description, int weatherCode, List<DayForecast> dailyForecast) {
            this.city = city;
            this.temperature = temperature;
            this.humidity = humidity;
            this.description = description;
            this.weatherCode = weatherCode;
            this.dailyForecast = dailyForecast != null ? dailyForecast : new ArrayList<>();
        }

        public WeatherInfo(String city, double temperature, int humidity, String description, int weatherCode) {
            this(city, temperature, humidity, description, weatherCode, new ArrayList<>());
        }
    }

    // Detailed 7-Day Forecast Item
    public static final class DetailDayForecast {
        public final String dateStr;
        public final String dayName;
        public final int weatherCode;
        public final String description;
        public final int minTemp;
        public final int maxTemp;
        public final int apparentMax;
        public final int apparentMin;
        public final double uvIndex;
        public final int rainProb;
        public final double windSpeedMax;

        public DetailDayForecast(String dateStr, String dayName, int weatherCode, String description,
                                 int minTemp, int maxTemp, int apparentMax, int apparentMin,
                                 double uvIndex, int rainProb, double windSpeedMax) {
            this.dateStr = dateStr;
            this.dayName = dayName;
            this.weatherCode = weatherCode;
            this.description = description;
            this.minTemp = minTemp;
            this.maxTemp = maxTemp;
            this.apparentMax = apparentMax;
            this.apparentMin = apparentMin;
            this.uvIndex = uvIndex;
            this.rainProb = rainProb;
            this.windSpeedMax = windSpeedMax;
        }
    }

    // Comprehensive Weather Info for Full-Screen Weather Detail View
    public static final class WeatherDetailInfo {
        public final String city;
        public final double currentTemp;
        public final double apparentTemp;
        public final int humidity;
        public final int weatherCode;
        public final String description;
        public final double pressure; // hPa
        public final double windSpeed; // km/h
        public final List<DetailDayForecast> dailyForecast;
        public final long timestamp;

        public WeatherDetailInfo(String city, double currentTemp, double apparentTemp, int humidity,
                                 int weatherCode, String description, double pressure, double windSpeed,
                                 List<DetailDayForecast> dailyForecast, long timestamp) {
            this.city = city;
            this.currentTemp = currentTemp;
            this.apparentTemp = apparentTemp;
            this.humidity = humidity;
            this.weatherCode = weatherCode;
            this.description = description;
            this.pressure = pressure;
            this.windSpeed = windSpeed;
            this.dailyForecast = dailyForecast != null ? dailyForecast : new ArrayList<>();
            this.timestamp = timestamp;
        }
    }

    public interface WeatherCallback {
        void onWeatherLoaded(WeatherInfo weather);
    }

    public interface WeatherDetailCallback {
        void onSuccess(WeatherDetailInfo detailInfo);
        void onError(Exception error);
    }

    private final SharedPreferences prefs;
    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "NextTV-WeatherSync");
        t.setDaemon(true);
        return t;
    });
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public WeatherDataManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // -------------------------------------------------------------
    // Location Management & Persistence
    // -------------------------------------------------------------
    public void saveLocation(String cityName, double lat, double lon, boolean isCustom) {
        prefs.edit()
                .putBoolean(KEY_LOCATION_CUSTOM, isCustom)
                .putString(KEY_LOCATION_CITY, cityName)
                .putString(KEY_LOCATION_LAT, String.valueOf(lat))
                .putString(KEY_LOCATION_LON, String.valueOf(lon))
                .apply();
    }

    public boolean isCustomLocation() {
        return prefs.getBoolean(KEY_LOCATION_CUSTOM, false);
    }

    public String getSavedCityName() {
        return prefs.getString(KEY_LOCATION_CITY, DEFAULT_CITY);
    }

    public double getSavedLatitude() {
        String latStr = prefs.getString(KEY_LOCATION_LAT, String.valueOf(DEFAULT_LAT));
        try {
            return Double.parseDouble(latStr);
        } catch (Exception e) {
            return DEFAULT_LAT;
        }
    }

    public double getSavedLongitude() {
        String lonStr = prefs.getString(KEY_LOCATION_LON, String.valueOf(DEFAULT_LON));
        try {
            return Double.parseDouble(lonStr);
        } catch (Exception e) {
            return DEFAULT_LON;
        }
    }

    // -------------------------------------------------------------
    // Basic Weather Cache (Used by Launcher Cold Start)
    // -------------------------------------------------------------
    public WeatherInfo getCachedWeather() {
        String jsonStr = prefs.getString(KEY_CACHE_JSON, null);
        if (jsonStr == null) return null;
        try {
            JSONObject obj = new JSONObject(jsonStr);
            List<DayForecast> forecasts = new ArrayList<>();
            JSONArray dailyArr = obj.optJSONArray("dailyForecast");
            if (dailyArr != null) {
                for (int i = 0; i < dailyArr.length(); i++) {
                    JSONObject dObj = dailyArr.getJSONObject(i);
                    forecasts.add(new DayForecast(
                            dObj.optString("dayName"),
                            dObj.optInt("weatherCode", 1),
                            dObj.optInt("minTemp", 24),
                            dObj.optInt("maxTemp", 32)
                    ));
                }
            }
            return new WeatherInfo(
                    obj.optString("city", getSavedCityName()),
                    obj.optDouble("temperature", 28.0),
                    obj.optInt("humidity", 70),
                    obj.optString("description", "Trời nhiều mây"),
                    obj.optInt("weatherCode", 1),
                    forecasts
            );
        } catch (Exception e) {
            return null;
        }
    }

    // -------------------------------------------------------------
    // Full Detailed Weather Cache
    // -------------------------------------------------------------
    public WeatherDetailInfo getCachedDetailWeather() {
        String jsonStr = prefs.getString(KEY_CACHE_DETAIL_JSON, null);
        if (jsonStr == null) return null;
        try {
            JSONObject obj = new JSONObject(jsonStr);
            List<DetailDayForecast> forecasts = new ArrayList<>();
            JSONArray dailyArr = obj.optJSONArray("dailyForecast");
            if (dailyArr != null) {
                for (int i = 0; i < dailyArr.length(); i++) {
                    JSONObject dObj = dailyArr.getJSONObject(i);
                    forecasts.add(new DetailDayForecast(
                            dObj.optString("dateStr"),
                            dObj.optString("dayName"),
                            dObj.optInt("weatherCode", 1),
                            dObj.optString("description"),
                            dObj.optInt("minTemp", 24),
                            dObj.optInt("maxTemp", 30),
                            dObj.optInt("apparentMax", 32),
                            dObj.optInt("apparentMin", 25),
                            dObj.optDouble("uvIndex", 5.0),
                            dObj.optInt("rainProb", 20),
                            dObj.optDouble("windSpeedMax", 10.0)
                    ));
                }
            }
            return new WeatherDetailInfo(
                    obj.optString("city", getSavedCityName()),
                    obj.optDouble("currentTemp", 28.0),
                    obj.optDouble("apparentTemp", 30.0),
                    obj.optInt("humidity", 70),
                    obj.optInt("weatherCode", 1),
                    obj.optString("description", "Có mây"),
                    obj.optDouble("pressure", 1013.0),
                    obj.optDouble("windSpeed", 8.0),
                    forecasts,
                    obj.optLong("timestamp", System.currentTimeMillis())
            );
        } catch (Exception e) {
            return null;
        }
    }

    // -------------------------------------------------------------
    // Asynchronous Detailed Forecast Fetching
    // -------------------------------------------------------------
    public void fetchDetailAsync(WeatherDetailCallback callback) {
        final double lat = getSavedLatitude();
        final double lon = getSavedLongitude();
        final String city = getSavedCityName();

        executor.execute(() -> {
            try {
                WeatherDetailInfo info = fetchDetailSync(lat, lon, city);
                if (info != null) {
                    mainHandler.post(() -> callback.onSuccess(info));
                } else {
                    mainHandler.post(() -> callback.onError(new Exception("Không có dữ liệu phản hồi")));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    /**
     * Synchronously fetches real 7-day meteorological forecast from Open-Meteo.
     * Must be called on a background worker thread.
     */
    public WeatherDetailInfo fetchDetailSync(double lat, double lon, String cityName) throws Exception {
        String urlStr = "https://api.open-meteo.com/v1/forecast?latitude=" + lat
                + "&longitude=" + lon
                + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,surface_pressure,wind_speed_10m"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_max,apparent_temperature_min,uv_index_max,precipitation_probability_max,wind_speed_10m_max"
                + "&timezone=auto&forecast_days=7";

        String jsonStr = httpGet(urlStr);
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            throw new Exception("Lỗi kết nối máy chủ thời tiết");
        }

        JSONObject root = new JSONObject(jsonStr);
        JSONObject current = root.optJSONObject("current");
        JSONObject daily = root.optJSONObject("daily");

        if (current == null || daily == null) {
            throw new Exception("Dữ liệu khí tượng không đúng định dạng");
        }

        double curTemp = current.optDouble("temperature_2m", 28.0);
        double appTemp = current.optDouble("apparent_temperature", curTemp);
        int humidity = current.optInt("relative_humidity_2m", 70);
        int code = current.optInt("weather_code", 1);
        double pressure = current.optDouble("surface_pressure", 1013.0);
        double windSpeed = current.optDouble("wind_speed_10m", 5.0);
        String desc = mapWeatherCode(code);

        JSONArray timeArr = daily.optJSONArray("time");
        JSONArray codeArr = daily.optJSONArray("weather_code");
        JSONArray maxArr = daily.optJSONArray("temperature_2m_max");
        JSONArray minArr = daily.optJSONArray("temperature_2m_min");
        JSONArray appMaxArr = daily.optJSONArray("apparent_temperature_max");
        JSONArray appMinArr = daily.optJSONArray("apparent_temperature_min");
        JSONArray uvArr = daily.optJSONArray("uv_index_max");
        JSONArray rainArr = daily.optJSONArray("precipitation_probability_max");
        JSONArray windMaxArr = daily.optJSONArray("wind_speed_10m_max");

        List<DetailDayForecast> dayList = new ArrayList<>();
        List<DayForecast> simpleList = new ArrayList<>();

        if (timeArr != null && codeArr != null && maxArr != null && minArr != null) {
            for (int i = 0; i < timeArr.length() && i < 7; i++) {
                String timeStr = timeArr.optString(i, "");
                String dayName = formatDayName(timeStr, i);
                int wCode = codeArr.optInt(i, 1);
                String dayDesc = mapWeatherCode(wCode);
                int maxT = (int) Math.round(maxArr.optDouble(i, 30.0));
                int minT = (int) Math.round(minArr.optDouble(i, 24.0));
                int appMax = appMaxArr != null ? (int) Math.round(appMaxArr.optDouble(i, maxT)) : maxT;
                int appMin = appMinArr != null ? (int) Math.round(appMinArr.optDouble(i, minT)) : minT;
                double uv = uvArr != null ? uvArr.optDouble(i, 5.0) : 0.0;
                int rain = rainArr != null ? rainArr.optInt(i, 0) : 0;
                double wind = windMaxArr != null ? windMaxArr.optDouble(i, 10.0) : 0.0;

                dayList.add(new DetailDayForecast(timeStr, dayName, wCode, dayDesc, minT, maxT, appMax, appMin, uv, rain, wind));

                if (i >= 1 && i <= 3) {
                    simpleList.add(new DayForecast(dayName, wCode, minT, maxT));
                }
            }
        }

        long now = System.currentTimeMillis();
        WeatherDetailInfo detailInfo = new WeatherDetailInfo(cityName, curTemp, appTemp, humidity, code, desc, pressure, windSpeed, dayList, now);

        // 1. Save Detail Cache to SharedPreferences
        try {
            JSONObject dCache = new JSONObject();
            dCache.put("city", cityName);
            dCache.put("currentTemp", curTemp);
            dCache.put("apparentTemp", appTemp);
            dCache.put("humidity", humidity);
            dCache.put("weatherCode", code);
            dCache.put("description", desc);
            dCache.put("pressure", pressure);
            dCache.put("windSpeed", windSpeed);
            dCache.put("timestamp", now);

            JSONArray dArr = new JSONArray();
            for (DetailDayForecast df : dayList) {
                JSONObject dObj = new JSONObject();
                dObj.put("dateStr", df.dateStr);
                dObj.put("dayName", df.dayName);
                dObj.put("weatherCode", df.weatherCode);
                dObj.put("description", df.description);
                dObj.put("minTemp", df.minTemp);
                dObj.put("maxTemp", df.maxTemp);
                dObj.put("apparentMax", df.apparentMax);
                dObj.put("apparentMin", df.apparentMin);
                dObj.put("uvIndex", df.uvIndex);
                dObj.put("rainProb", df.rainProb);
                dObj.put("windSpeedMax", df.windSpeedMax);
                dArr.put(dObj);
            }
            dCache.put("dailyForecast", dArr);

            // 2. Also refresh widget basic cache
            JSONObject bCache = new JSONObject();
            bCache.put("city", cityName);
            bCache.put("temperature", curTemp);
            bCache.put("humidity", humidity);
            bCache.put("description", desc);
            bCache.put("weatherCode", code);

            JSONArray bArr = new JSONArray();
            for (DayForecast bf : simpleList) {
                JSONObject bObj = new JSONObject();
                bObj.put("dayName", bf.dayName);
                bObj.put("weatherCode", bf.weatherCode);
                bObj.put("minTemp", bf.minTemp);
                bObj.put("maxTemp", bf.maxTemp);
                bArr.put(bObj);
            }
            bCache.put("dailyForecast", bArr);

            prefs.edit()
                    .putString(KEY_CACHE_DETAIL_JSON, dCache.toString())
                    .putLong(KEY_CACHE_DETAIL_TIMESTAMP, now)
                    .putString(KEY_CACHE_JSON, bCache.toString())
                    .putLong(KEY_CACHE_TIMESTAMP, now)
                    .apply();
        } catch (Exception ignored) {}

        return detailInfo;
    }

    public static String mapWeatherCode(int code) {
        if (code == 0) return "Trời quang đãng";
        if (code >= 1 && code <= 3) return "Có mây rải rác";
        if (code == 45 || code == 48) return "Sương mù";
        if (code >= 51 && code <= 67) return "Mưa rào nhẹ";
        if (code >= 71 && code <= 77) return "Mưa tuyết / lạnh";
        if (code >= 80 && code <= 82) return "Mưa rào rải rác";
        if (code >= 95 && code <= 99) return "Có dông sét";
        return "Nhiều mây";
    }

    public static String formatDayName(String dateStr, int offsetFromToday) {
        if (offsetFromToday == 0) return "Hôm nay";
        if (offsetFromToday == 1) return "Mai";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date d = sdf.parse(dateStr);
            if (d != null) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(d);
                int day = cal.get(Calendar.DAY_OF_WEEK);
                switch (day) {
                    case Calendar.MONDAY: return "T2";
                    case Calendar.TUESDAY: return "T3";
                    case Calendar.WEDNESDAY: return "T4";
                    case Calendar.THURSDAY: return "T5";
                    case Calendar.FRIDAY: return "T6";
                    case Calendar.SATURDAY: return "T7";
                    case Calendar.SUNDAY: return "CN";
                }
            }
        } catch (Exception ignored) {}
        return "T" + (offsetFromToday + 1);
    }

    private static String httpGet(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "NextTV-Launcher/1.0");

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                return sb.toString();
            }
        } catch (Exception e) {
            Log.w(TAG, "HTTP GET failed for " + urlStr + ": " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }
}
