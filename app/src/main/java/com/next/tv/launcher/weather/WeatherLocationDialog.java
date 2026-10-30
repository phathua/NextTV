package com.next.tv.launcher.weather;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.next.tv.launcher.R;
import com.next.tv.launcher.widget.WeatherDataManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight, D-pad accessible dialog for switching weather location.
 * Stores selection in SharedPreferences via WeatherDataManager.
 */
public class WeatherLocationDialog extends Dialog {

    public interface OnLocationChangeListener {
        void onLocationChanged(String cityName, double lat, double lon, boolean isCustom);
    }

    public static class CityEntry {
        public final String name;
        public final double lat;
        public final double lon;
        public final boolean isCustom;

        public CityEntry(String name, double lat, double lon, boolean isCustom) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
            this.isCustom = isCustom;
        }
    }

    private final WeatherDataManager weatherManager;
    private final OnLocationChangeListener listener;
    private final List<CityEntry> cities = new ArrayList<>();

    public WeatherLocationDialog(@NonNull Context context, WeatherDataManager weatherManager, OnLocationChangeListener listener) {
        super(context);
        this.weatherManager = weatherManager;
        this.listener = listener;
        initCityList();
    }

    private void initCityList() {
        cities.add(new CityEntry("Tự động (Theo mạng IP)", WeatherDataManager.DEFAULT_LAT, WeatherDataManager.DEFAULT_LON, false));
        cities.add(new CityEntry("Hà Nội", 21.0285, 105.8542, true));
        cities.add(new CityEntry("TP. Hồ Chí Minh", 10.8231, 106.6297, true));
        cities.add(new CityEntry("Đà Nẵng", 16.0685, 108.2215, true));
        cities.add(new CityEntry("Hải Phòng", 20.8449, 106.6881, true));
        cities.add(new CityEntry("Cần Thơ", 10.0452, 105.7469, true));
        cities.add(new CityEntry("Nha Trang", 12.2388, 109.1967, true));
        cities.add(new CityEntry("Huế", 16.4637, 107.5909, true));
        cities.add(new CityEntry("Đà Lạt", 11.9404, 108.4583, true));
        cities.add(new CityEntry("Quy Nhơn", 13.7830, 109.2197, true));
        cities.add(new CityEntry("Vũng Tàu", 10.3460, 107.0843, true));
        cities.add(new CityEntry("Buôn Ma Thuột", 12.6667, 108.0500, true));
        cities.add(new CityEntry("Hạ Long", 20.9505, 107.0734, true));
        cities.add(new CityEntry("Vinh", 18.6734, 105.6813, true));
        cities.add(new CityEntry("Phan Thiết", 10.9298, 108.0995, true));
    }

    private void selectCity(CityEntry selected) {
        if (selected == null) return;
        weatherManager.saveLocation(selected.name, selected.lat, selected.lon, selected.isCustom);
        if (listener != null) {
            listener.onLocationChanged(selected.name, selected.lat, selected.lon, selected.isCustom);
        }
        dismiss();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_weather_location);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }

        ListView listView = findViewById(R.id.citiesListView);
        if (listView != null) {
            ArrayAdapter<CityEntry> adapter = new ArrayAdapter<CityEntry>(getContext(), R.layout.item_weather_location_city, cities) {
                @NonNull
                @Override
                public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                    TextView tv = (TextView) (convertView != null ? convertView :
                            LayoutInflater.from(getContext()).inflate(R.layout.item_weather_location_city, parent, false));
                    CityEntry entry = getItem(position);
                    if (entry != null) {
                        tv.setText(entry.name);
                        boolean isSelected;
                        if (!weatherManager.isCustomLocation() && !entry.isCustom) {
                            isSelected = true;
                        } else {
                            isSelected = weatherManager.isCustomLocation() && weatherManager.getSavedCityName().equals(entry.name);
                        }
                        tv.setSelected(isSelected);
                    }
                    return tv;
                }
            };
            listView.setAdapter(adapter);

            listView.setOnItemClickListener((parent, view, position, id) -> {
                selectCity(cities.get(position));
            });

            listView.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                        int pos = listView.getSelectedItemPosition();
                        if (pos >= 0 && pos < cities.size()) {
                            selectCity(cities.get(pos));
                            return true;
                        }
                    }
                }
                return false;
            });

            listView.post(() -> {
                int selectedIndex = 0;
                for (int i = 0; i < cities.size(); i++) {
                    CityEntry e = cities.get(i);
                    if (!weatherManager.isCustomLocation() && !e.isCustom) {
                        selectedIndex = i;
                        break;
                    } else if (weatherManager.isCustomLocation() && weatherManager.getSavedCityName().equals(e.name)) {
                        selectedIndex = i;
                        break;
                    }
                }
                listView.setSelection(selectedIndex);
                listView.requestFocus();
            });
        }
    }
}
