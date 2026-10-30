package com.next.tv.launcher.lock;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.next.tv.launcher.R;

public class KidTimeUpDialog extends Dialog {

    public interface OnKidModeExitListener {
        void onKidModeExited();
    }

    private final Context context;
    private final OnKidModeExitListener exitListener;
    private final boolean isCurfew;

    public KidTimeUpDialog(@NonNull Context context, boolean isCurfew, OnKidModeExitListener exitListener) {
        super(context, R.style.Theme_TvHome);
        this.context = context;
        this.isCurfew = isCurfew;
        this.exitListener = exitListener;
        setCancelable(false);
        setCanceledOnTouchOutside(false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_kid_time_up);

        Window window = getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#F509090B")));
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }

        TextView titleView = findViewById(R.id.timeUpTitle);
        TextView subtitleView = findViewById(R.id.timeUpSubtitle);
        ImageView heroIcon = findViewById(R.id.imgCurfewHeroIcon);

        View btnExtend = findViewById(R.id.btnExtendTime);
        View btnExit = findViewById(R.id.btnExitKidMode);
        View btnSleep = findViewById(R.id.btnSleepTv);

        ImageView iconExtend = findViewById(R.id.iconExtendTime);
        TextView titleExtend = findViewById(R.id.titleExtendTime);
        TextView subExtend = findViewById(R.id.subExtendTime);

        ImageView iconExit = findViewById(R.id.iconExitKidMode);
        TextView titleExit = findViewById(R.id.titleExitKidMode);
        TextView subExit = findViewById(R.id.subExitKidMode);

        ImageView iconSleep = findViewById(R.id.iconSleepTv);
        TextView titleSleep = findViewById(R.id.titleSleepTv);
        TextView subSleep = findViewById(R.id.subSleepTv);

        if (isCurfew) {
            titleView.setText("Đến giờ đi ngủ");
            subtitleView.setText("Đã đến giờ giới nghiêm buổi tối. Bé hãy nghỉ ngơi nhé.");
            if (heroIcon != null) heroIcon.setImageResource(R.drawable.ic_sleep_curfew);
        } else {
            titleView.setText("Hết giờ xem TV");
            subtitleView.setText("Bé đã dùng hết thời gian xem hôm nay rồi.");
            if (heroIcon != null) heroIcon.setImageResource(R.drawable.ic_time_extend);
        }

        setupCardFocus(btnExtend, iconExtend, titleExtend, subExtend, "#10B981");
        setupCardFocus(btnExit, iconExit, titleExit, subExit, "#A1A1AA");
        setupCardFocus(btnSleep, iconSleep, titleSleep, subSleep, "#38BDF8");

        btnExtend.setOnClickListener(v -> {
            PinLockDialog pinDialog = new PinLockDialog(context, PinLockDialog.MODE_VERIFY,
                    "Mã PIN phụ huynh", "Nhập mã để gia hạn thêm",
                    new PinLockDialog.PinResultListener() {
                        @Override
                        public void onSuccess() {
                            KidTimeQuotaTracker.getInstance(context).addBonusMinutes(30);
                            Toast.makeText(context, "Đã gia hạn 30 phút", Toast.LENGTH_SHORT).show();
                            dismiss();
                        }

                        @Override
                        public void onCancel() {}
                    });
            pinDialog.show();
        });

        btnExit.setOnClickListener(v -> {
            PinLockDialog pinDialog = new PinLockDialog(context, PinLockDialog.MODE_VERIFY,
                    "Mã PIN phụ huynh", "Nhập mã để tắt chế độ",
                    new PinLockDialog.PinResultListener() {
                        @Override
                        public void onSuccess() {
                            AppLockManager.getInstance(context).setKidModeActive(false);
                            Toast.makeText(context, "Đã tắt chế độ trẻ em", Toast.LENGTH_SHORT).show();
                            dismiss();
                            if (exitListener != null) exitListener.onKidModeExited();
                        }

                        @Override
                        public void onCancel() {}
                    });
            pinDialog.show();
        });

        btnSleep.setOnClickListener(v -> {
            try {
                Intent dreamIntent = new Intent("android.intent.action.DREAMING_START");
                dreamIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(dreamIntent);
            } catch (Exception e) {
                try {
                    Runtime.getRuntime().exec(new String[]{"input", "keyevent", "223"});
                } catch (Exception ignored) {}
            }
        });

        btnExtend.post(() -> btnExtend.requestFocus());
    }

    private void setupCardFocus(View card, ImageView icon, TextView title, TextView sub, String defaultSubColor) {
        if (card == null) return;
        card.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate().scaleX(hasFocus ? 1.05f : 1.0f).scaleY(hasFocus ? 1.05f : 1.0f).setDuration(130).start();
            if (title != null) title.setTextColor(Color.parseColor(hasFocus ? "#09090B" : "#F4F4F5"));
            if (sub != null) sub.setTextColor(Color.parseColor(hasFocus ? "#52525B" : defaultSubColor));
            if (icon != null) {
                if (hasFocus) {
                    icon.setColorFilter(Color.parseColor("#09090B"));
                } else {
                    icon.clearColorFilter();
                }
            }
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            Toast.makeText(context, "Cần mã PIN phụ huynh để mở", Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
