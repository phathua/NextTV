package com.next.tv.launcher.weather;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.next.tv.launcher.R;
import com.next.tv.launcher.widget.WeatherDataManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 100% Native Java + XML Full-screen Weather Detail Experience for Android TV.
 * Optimized for low-RAM hardware (PSS < 150 MB, GfxDev <= 40 MB).
 * Single entrance animation (200ms) with zero continuous runtime loops.
 * 2D D-pad orthogonal navigation with boundary locks and live metrics updates.
 */
public class WeatherDetailActivity extends Activity {
    private static final String TAG = "TvHome.WeatherDetail";

    public static final String EXTRA_LOCATION_CHANGED = "extra_weather_location_changed";

    private WeatherDataManager weatherManager;
    private float density;

    private View rootView;
    private ImageView ivBg;
    private TextView tvCityTitle;
    private TextView tvConditionSubtitle;
    private TextView tvDateTime;
    private TextView btnChangeLocation;

    // Hero Section Views
    private TextView tvHeroTemp;
    private ImageView ivHeroIcon;
    private TextView tvHeroCondition;
    private TextView tvHeroHighLow;
    private TextView tvHeroFeelsRain;

    // Metric Grid Views
    private TextView tvHumidity;
    private TextView tvWind;
    private TextView tvPressure;
    private TextView tvUv;
    private TextView tvApparent;
    private TextView tvRainProb;

    // Bottom Forecast Rail
    private LinearLayout forecastRow;
    private final List<View> dayCardViews = new ArrayList<>();

    // Loading & Error Containers
    private View loadingContainer;
    private View errorContainer;
    private TextView btnRetry;

    private WeatherDataManager.WeatherDetailInfo currentDetailInfo;
    private int selectedDayIndex = 0;
    private boolean locationHasChanged = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weather_detail);

        density = getResources().getDisplayMetrics().density;
        weatherManager = new WeatherDataManager(this);

        initViews();
        setupEventListeners();

        // 1. Single entrance animation (200ms) - Ceases immediately after running
        rootView.setAlpha(0f);
        rootView.setTranslationY(18f * density);
        rootView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(200)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // 2. Load weather data (instant cache or network)
        loadWeatherData();
    }

    private void initViews() {
        rootView = findViewById(R.id.weatherRootView);
        ivBg = findViewById(R.id.weatherBgImage);
        tvCityTitle = findViewById(R.id.weatherCityTitle);
        tvConditionSubtitle = findViewById(R.id.weatherConditionSubtitle);
        tvDateTime = findViewById(R.id.weatherDateTimeText);
        btnChangeLocation = findViewById(R.id.weatherChangeLocationBtn);

        tvHeroTemp = findViewById(R.id.heroTempText);
        ivHeroIcon = findViewById(R.id.heroWeatherIcon);
        tvHeroCondition = findViewById(R.id.heroConditionText);
        tvHeroHighLow = findViewById(R.id.heroHighLowText);
        tvHeroFeelsRain = findViewById(R.id.heroFeelsRainText);

        tvHumidity = findViewById(R.id.metricHumidityValue);
        tvWind = findViewById(R.id.metricWindValue);
        tvPressure = findViewById(R.id.metricPressureValue);
        tvUv = findViewById(R.id.metricUvValue);
        tvApparent = findViewById(R.id.metricApparentValue);
        tvRainProb = findViewById(R.id.metricRainProbValue);

        forecastRow = findViewById(R.id.forecastDaysRow);
        loadingContainer = findViewById(R.id.weatherLoadingContainer);
        errorContainer = findViewById(R.id.weatherErrorContainer);
        btnRetry = findViewById(R.id.weatherRetryBtn);

        // Header time & location setup
        tvCityTitle.setText(weatherManager.getSavedCityName());
        updateSystemDateTimeHeader();
    }

    private void setupEventListeners() {
        btnChangeLocation.setOnClickListener(v -> showLocationDialog());

        btnChangeLocation.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (selectedDayIndex >= 0 && selectedDayIndex < dayCardViews.size()) {
                    dayCardViews.get(selectedDayIndex).requestFocus();
                    return true;
                }
            }
            return false;
        });

        if (btnRetry != null) {
            btnRetry.setOnClickListener(v -> {
                errorContainer.setVisibility(View.GONE);
                loadingContainer.setVisibility(View.VISIBLE);
                fetchFreshWeather();
            });
        }
    }

    private void updateSystemDateTimeHeader() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String timeStr = timeFmt.format(cal.getTime());

        String[] days = {"Chủ nhật", "Thứ hai", "Thứ ba", "Thứ tư", "Thứ năm", "Thứ sáu", "Thứ bảy"};
        String dayOfWeek = days[cal.get(Calendar.DAY_OF_WEEK) - 1];

        SimpleDateFormat dateFmt = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        String dateStr = dateFmt.format(cal.getTime());

        tvDateTime.setText(timeStr + " · " + dayOfWeek + ", " + dateStr);
    }

    private void loadWeatherData() {
        // Fast instant cached display (0 ms execution)
        WeatherDataManager.WeatherDetailInfo cached = weatherManager.getCachedDetailWeather();
        if (cached != null) {
            bindWeatherDetail(cached);
        } else {
            loadingContainer.setVisibility(View.VISIBLE);
        }

        // Fetch fresh data in background
        fetchFreshWeather();
    }

    private void fetchFreshWeather() {
        weatherManager.fetchDetailAsync(new WeatherDataManager.WeatherDetailCallback() {
            @Override
            public void onSuccess(WeatherDataManager.WeatherDetailInfo detailInfo) {
                if (isFinishing() || isDestroyed()) return;
                loadingContainer.setVisibility(View.GONE);
                errorContainer.setVisibility(View.GONE);
                bindWeatherDetail(detailInfo);
            }

            @Override
            public void onError(Exception error) {
                if (isFinishing() || isDestroyed()) return;
                loadingContainer.setVisibility(View.GONE);
                if (currentDetailInfo == null) {
                    errorContainer.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void bindWeatherDetail(WeatherDataManager.WeatherDetailInfo info) {
        if (info == null) return;
        this.currentDetailInfo = info;

        tvCityTitle.setText(info.city);
        tvConditionSubtitle.setText(info.description);

        // Update Background based on weather and time of day
        int bgRes = getWeatherBackgroundRes(info.weatherCode);
        ivBg.setImageResource(bgRes);

        // Populate bottom 7-day forecast cards
        buildForecastCards(info.dailyForecast);

        // Display current day index (0 = Today)
        updateSelectedDayDisplay(0);
    }

    private void buildForecastCards(List<WeatherDataManager.DetailDayForecast> dailyList) {
        forecastRow.removeAllViews();
        dayCardViews.clear();

        if (dailyList == null || dailyList.isEmpty()) return;

        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < dailyList.size(); i++) {
            final int index = i;
            final WeatherDataManager.DetailDayForecast day = dailyList.get(i);

            View card = inflater.inflate(R.layout.item_weather_day_card, forecastRow, false);

            // Add margin between cards (except the last one)
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) card.getLayoutParams();
            if (i < dailyList.size() - 1) {
                lp.setMarginEnd((int) (8 * density));
            } else {
                lp.setMarginEnd(0);
            }
            card.setLayoutParams(lp);

            TextView tvName = card.findViewById(R.id.dayNameText);
            TextView tvDate = card.findViewById(R.id.dayDateText);
            ImageView ivIcon = card.findViewById(R.id.dayWeatherIcon);
            TextView tvTemp = card.findViewById(R.id.dayTempText);
            TextView tvRain = card.findViewById(R.id.dayRainText);

            tvName.setText(day.dayName);
            tvDate.setText(formatShortDate(day.dateStr));
            ivIcon.setImageResource(getWeatherIconRes(day.weatherCode));
            tvTemp.setText(day.minTemp + "° - " + day.maxTemp + "°");
            tvRain.setText("Mưa " + day.rainProb + "%");

            // Focus and selection handling
            card.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    updateSelectedDayDisplay(index);
                }
            });

            card.setOnClickListener(v -> updateSelectedDayDisplay(index));

            // D-pad Boundary Lock
            card.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;

                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && index == 0) {
                    // Lock at the beginning: do not lose focus or jump elsewhere
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && index == dailyList.size() - 1) {
                    // Lock at the end: do not lose focus or jump elsewhere
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    btnChangeLocation.requestFocus();
                    return true;
                }
                return false;
            });

            forecastRow.addView(card);
            dayCardViews.add(card);
        }

        // Focus first card by default
        if (!dayCardViews.isEmpty()) {
            dayCardViews.get(0).post(() -> dayCardViews.get(0).requestFocus());
        }
    }

    private void updateSelectedDayDisplay(int index) {
        if (currentDetailInfo == null || currentDetailInfo.dailyForecast.isEmpty()) return;
        if (index < 0 || index >= currentDetailInfo.dailyForecast.size()) return;

        this.selectedDayIndex = index;
        WeatherDataManager.DetailDayForecast day = currentDetailInfo.dailyForecast.get(index);

        // Update card selected states
        for (int i = 0; i < dayCardViews.size(); i++) {
            dayCardViews.get(i).setSelected(i == index);
        }

        // 1. Update Hero Left Section
        if (index == 0) {
            tvHeroTemp.setText(Math.round(currentDetailInfo.currentTemp) + "°");
            ivHeroIcon.setImageResource(getWeatherIconRes(currentDetailInfo.weatherCode));
            tvHeroCondition.setText(currentDetailInfo.description);
            tvHeroHighLow.setText("Cao nhất: " + day.maxTemp + "° · Thấp nhất: " + day.minTemp + "°");
            tvHeroFeelsRain.setText("Cảm giác như " + Math.round(currentDetailInfo.apparentTemp) + "° · Khả năng mưa " + day.rainProb + "%");
        } else {
            tvHeroTemp.setText(day.maxTemp + "°");
            ivHeroIcon.setImageResource(getWeatherIconRes(day.weatherCode));
            tvHeroCondition.setText(day.description);
            tvHeroHighLow.setText("Cao nhất: " + day.maxTemp + "° · Thấp nhất: " + day.minTemp + "°");
            tvHeroFeelsRain.setText("Cảm giác như " + day.apparentMax + "° · Khả năng mưa " + day.rainProb + "%");
        }

        // 2. Update Right 6-Metric Grid
        if (index == 0) {
            tvHumidity.setText(currentDetailInfo.humidity + "%");
            tvWind.setText(Math.round(currentDetailInfo.windSpeed) + " km/h");
            tvPressure.setText(Math.round(currentDetailInfo.pressure) + " hPa");
            tvUv.setText(formatUvIndex(day.uvIndex));
            tvApparent.setText(Math.round(currentDetailInfo.apparentTemp) + "°");
            tvRainProb.setText(day.rainProb + "%");
        } else {
            tvHumidity.setText(currentDetailInfo.humidity + "%");
            tvWind.setText(Math.round(day.windSpeedMax) + " km/h");
            tvPressure.setText(Math.round(currentDetailInfo.pressure) + " hPa");
            tvUv.setText(formatUvIndex(day.uvIndex));
            tvApparent.setText(day.apparentMax + "°");
            tvRainProb.setText(day.rainProb + "%");
        }
    }

    private String formatUvIndex(double uv) {
        String level = (uv >= 8.0) ? "Rất cao" : (uv >= 6.0) ? "Cao" : (uv >= 3.0) ? "Vừa" : "Thấp";
        return String.format(Locale.US, "%.1f (%s)", uv, level);
    }

    private String formatShortDate(String dateStr) {
        try {
            SimpleDateFormat inFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date d = inFmt.parse(dateStr);
            if (d != null) {
                return new SimpleDateFormat("dd/MM", Locale.getDefault()).format(d);
            }
        } catch (Exception ignored) {}
        return dateStr;
    }

    private void showLocationDialog() {
        WeatherLocationDialog dialog = new WeatherLocationDialog(this, weatherManager, (cityName, lat, lon, isCustom) -> {
            locationHasChanged = true;
            tvCityTitle.setText(cityName);
            loadingContainer.setVisibility(View.VISIBLE);
            fetchFreshWeather();
        });
        dialog.show();
    }

    private int getWeatherIconRes(int code) {
        if (code == 0) return R.drawable.ic_weather_sun;
        if (code >= 1 && code <= 3) return R.drawable.ic_weather_cloud;
        if (code == 45 || code == 48) return R.drawable.ic_weather_cloud;
        if (code >= 51 && code <= 67) return R.drawable.ic_weather_rain;
        if (code >= 71 && code <= 77) return R.drawable.ic_weather_rain;
        if (code >= 80 && code <= 82) return R.drawable.ic_weather_rain;
        if (code >= 95 && code <= 99) return R.drawable.ic_weather_bolt;
        return R.drawable.ic_weather_cloud;
    }

    private int getWeatherBackgroundRes(int weatherCode) {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        boolean isNight = (hour >= 18 || hour < 5);
        boolean isDusk = (hour >= 16 && hour < 18);

        if (weatherCode >= 51) {
            return R.drawable.bg_weather_rain;
        }
        if (isNight) {
            return R.drawable.bg_weather_night;
        }
        if (isDusk) {
            return R.drawable.bg_weather_dusk;
        }
        if (weatherCode >= 1 && weatherCode <= 3) {
            return R.drawable.bg_weather_clouds;
        }
        return R.drawable.bg_weather_clear;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if (locationHasChanged) {
                    setResult(RESULT_OK);
                }
                finish();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDestroy() {
        if (ivBg != null) {
            ivBg.setImageDrawable(null);
        }
        if (ivHeroIcon != null) {
            ivHeroIcon.setImageDrawable(null);
        }
        dayCardViews.clear();
        super.onDestroy();
    }
}
