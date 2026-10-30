package com.next.tv.launcher.widget;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.next.tv.launcher.R;

public final class WidgetManagerDialog extends Dialog {
    public interface OnWidgetSettingsChangedListener {
        void onWidgetSettingsChanged();
    }

    private final Context context;
    private final WidgetConfigManager configManager;
    private final OnWidgetSettingsChangedListener listener;

    public WidgetManagerDialog(@NonNull Context context, OnWidgetSettingsChangedListener listener) {
        super(context, R.style.Theme_TvHome);
        this.context = context;
        this.configManager = WidgetConfigManager.getInstance(context);
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.70f);
        }

        View root = LayoutInflater.from(context).inflate(R.layout.popup_inputs, null);
        root.setBackgroundResource(R.drawable.glass_panel);
        root.setPadding(24, 24, 24, 24);

        LinearLayout rows = root.findViewById(R.id.rows);

        // Header Title
        TextView titleView = new TextView(context);
        titleView.setText("Quản lý tiện ích");
        titleView.setTextColor(Color.parseColor("#F4F4F5"));
        titleView.setTextSize(18f);
        titleView.setPadding(16, 8, 16, 4);
        rows.addView(titleView);

        TextView subView = new TextView(context);
        subView.setText("Bấm để bật hoặc tắt tiện ích");
        subView.setTextColor(Color.parseColor("#71717A"));
        subView.setTextSize(12f);
        subView.setPadding(16, 0, 16, 16);
        rows.addView(subView);

        // 5 Toggle Rows
        addToggleRow(rows, "Thời tiết", configManager.isWeatherEnabled(), enabled -> {
            configManager.setWeatherEnabled(enabled);
        });

        addToggleRow(rows, "Video thịnh hành", configManager.isYouTubeEnabled(), enabled -> {
            configManager.setYouTubeEnabled(enabled);
        });

        addToggleRow(rows, "Tin tức nóng", configManager.isNewsEnabled(), enabled -> {
            configManager.setNewsEnabled(enabled);
        });

        addToggleRow(rows, "Thời gian xem TV", configManager.isUsageEnabled(), enabled -> {
            configManager.setUsageEnabled(enabled);
        });

        addToggleRow(rows, "Đang phát nhạc", configManager.isMediaEnabled(), enabled -> {
            configManager.setMediaEnabled(enabled);
        });

        addToggleRow(rows, "Tỉ số thể thao", configManager.isFootballEnabled(), enabled -> {
            configManager.setFootballEnabled(enabled);
        });

        // Bottom Actions Container
        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(16, 20, 16, 8);

        Button btnDisableAll = new Button(context);
        btnDisableAll.setText("Tắt tất cả");
        btnDisableAll.setTextColor(Color.parseColor("#EF4444"));
        btnDisableAll.setTextSize(13f);
        btnDisableAll.setBackgroundResource(R.drawable.search_pill_btn_bg);
        LinearLayout.LayoutParams lpDisable = new LinearLayout.LayoutParams(0, (int) (38 * context.getResources().getDisplayMetrics().density), 1f);
        lpDisable.setMarginEnd(12);
        btnDisableAll.setLayoutParams(lpDisable);
        btnDisableAll.setOnClickListener(v -> {
            configManager.disableAllWidgets();
            dismiss();
            if (listener != null) listener.onWidgetSettingsChanged();
        });
        actions.addView(btnDisableAll);

        Button btnDone = new Button(context);
        btnDone.setText("Hoàn tất");
        btnDone.setTextColor(Color.parseColor("#10B981"));
        btnDone.setTextSize(13f);
        btnDone.setBackgroundResource(R.drawable.search_pill_btn_bg);
        LinearLayout.LayoutParams lpDone = new LinearLayout.LayoutParams(0, (int) (38 * context.getResources().getDisplayMetrics().density), 1f);
        btnDone.setLayoutParams(lpDone);
        btnDone.setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onWidgetSettingsChanged();
        });
        actions.addView(btnDone);

        rows.addView(actions);

        float density = context.getResources().getDisplayMetrics().density;
        setContentView(root, new ViewGroup.LayoutParams((int) (380 * density), ViewGroup.LayoutParams.WRAP_CONTENT));

        setOnDismissListener(dialog -> {
            if (listener != null) listener.onWidgetSettingsChanged();
        });
    }

    private interface OnToggleListener {
        void onToggled(boolean newState);
    }

    private void addToggleRow(LinearLayout container, String title, boolean initialEnabled, OnToggleListener toggleListener) {
        View row = LayoutInflater.from(context).inflate(R.layout.item_input, container, false);
        row.findViewById(R.id.icon).setVisibility(View.GONE);
        View dot = row.findViewById(R.id.dot);
        TextView status = row.findViewById(R.id.status);
        TextView titleText = row.findViewById(R.id.title);

        titleText.setText(title);

        final boolean[] state = new boolean[]{initialEnabled};
        updateRowState(dot, status, state[0]);

        row.setOnClickListener(v -> {
            state[0] = !state[0];
            updateRowState(dot, status, state[0]);
            if (toggleListener != null) toggleListener.onToggled(state[0]);
        });

        row.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate().scaleX(hasFocus ? 1.03f : 1f).scaleY(hasFocus ? 1.03f : 1f).setDuration(120).start();
        });

        container.addView(row);
    }

    private void updateRowState(View dot, TextView status, boolean enabled) {
        if (dot != null) {
            dot.setBackgroundColor(enabled ? Color.parseColor("#10B981") : Color.parseColor("#52525B"));
        }
        if (status != null) {
            status.setText(enabled ? "Đang bật" : "Đã tắt");
            status.setTextColor(enabled ? Color.parseColor("#10B981") : Color.parseColor("#71717A"));
        }
    }
}