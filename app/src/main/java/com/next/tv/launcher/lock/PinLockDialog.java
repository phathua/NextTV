package com.next.tv.launcher.lock;

import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.next.tv.launcher.R;

public class PinLockDialog extends Dialog {

    public static final int MODE_VERIFY = 0;
    public static final int MODE_CREATE = 1;
    public static final int MODE_REMOVE = 2;

    public interface PinResultListener {
        void onSuccess();
        void onCancel();
    }

    public interface PinValueListener {
        void onPinEntered(String pin);
        void onCancel();
    }

    private final AppLockManager lockManager;
    private final PinResultListener listener;
    private final PinValueListener valueListener;
    private final int mode;
    private final String customTitle;
    private final String customSubtitle;

    private TextView titleView;
    private TextView subtitleView;
    private TextView[] pinDots;
    private TextView errorTextView;
    private View dotsContainer;

    private final StringBuilder enteredPin = new StringBuilder();
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable countdownRunnable;

    public PinLockDialog(@NonNull Context context, PinResultListener listener) {
        this(context, MODE_VERIFY, null, null, listener);
    }

    public PinLockDialog(@NonNull Context context, String title, String subtitle, PinResultListener listener) {
        this(context, MODE_VERIFY, title, subtitle, listener);
    }

    public PinLockDialog(@NonNull Context context, int mode, String title, String subtitle, PinResultListener listener) {
        super(context, R.style.Theme_TvHome);
        this.lockManager = AppLockManager.getInstance(context);
        this.mode = mode;
        this.customTitle = title;
        this.customSubtitle = subtitle;
        this.listener = listener;
        this.valueListener = null;
    }

    public PinLockDialog(@NonNull Context context, int mode, String title, String subtitle, PinValueListener valueListener) {
        super(context, R.style.Theme_TvHome);
        this.lockManager = AppLockManager.getInstance(context);
        this.mode = mode;
        this.customTitle = title;
        this.customSubtitle = subtitle;
        this.listener = null;
        this.valueListener = valueListener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_pin_lock);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.75f);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }

        titleView = findViewById(R.id.pinTitle);
        subtitleView = findViewById(R.id.pinSubtitle);
        errorTextView = findViewById(R.id.pinErrorText);
        dotsContainer = findViewById(R.id.pinDotsContainer);

        if (customTitle != null) {
            titleView.setText(customTitle);
        } else {
            if (mode == MODE_CREATE || (!lockManager.hasPin() && mode == MODE_VERIFY)) {
                titleView.setText("Cài đặt mã PIN");
            } else if (mode == MODE_REMOVE) {
                titleView.setText("Tắt mã khóa");
            } else {
                titleView.setText("Nhập mã PIN");
            }
        }

        if (customSubtitle != null) {
            subtitleView.setText(customSubtitle);
        } else {
            if (mode == MODE_CREATE || (!lockManager.hasPin() && mode == MODE_VERIFY)) {
                subtitleView.setText("Nhập 4 số bảo vệ");
            } else if (mode == MODE_REMOVE) {
                subtitleView.setText("Nhập mã PIN hiện tại");
            } else {
                subtitleView.setText("Nhập mã PIN 4 số");
            }
        }

        pinDots = new TextView[]{
                findViewById(R.id.pinDot1),
                findViewById(R.id.pinDot2),
                findViewById(R.id.pinDot3),
                findViewById(R.id.pinDot4)
        };

        setupKeypad();
        updateDots();
        checkLockout();
    }

    private void setupKeypad() {
        int[] numIds = {
                R.id.key0, R.id.key1, R.id.key2, R.id.key3, R.id.key4,
                R.id.key5, R.id.key6, R.id.key7, R.id.key8, R.id.key9
        };

        for (int id : numIds) {
            Button btn = findViewById(id);
            if (btn != null) {
                final String digit = btn.getText().toString();
                btn.setOnClickListener(v -> onDigitPressed(digit));
            }
        }

        Button btnClear = findViewById(R.id.keyClear);
        if (btnClear != null) {
            btnClear.setOnClickListener(v -> deleteDigit());
        }

        Button btnCancel = findViewById(R.id.keyCancel);
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> {
                dismiss();
                if (listener != null) listener.onCancel();
            });
        }

        // Default focus on key 5 (center) or key 1
        View key5 = findViewById(R.id.key5);
        if (key5 != null) key5.requestFocus();
    }

    private void onDigitPressed(String digit) {
        if (lockManager.getRemainingLockoutSeconds() > 0) return;
        if (enteredPin.length() < 4) {
            enteredPin.append(digit);
            updateDots();
            if (enteredPin.length() == 4) {
                verify();
            }
        }
    }

    private void deleteDigit() {
        if (enteredPin.length() > 0) {
            enteredPin.deleteCharAt(enteredPin.length() - 1);
            updateDots();
            if (errorTextView != null) errorTextView.setVisibility(View.INVISIBLE);
        }
    }

    private void updateDots() {
        int len = enteredPin.length();
        for (int i = 0; i < 4; i++) {
            if (i < len) {
                pinDots[i].setText(String.valueOf(enteredPin.charAt(i)));
                pinDots[i].setTextColor(Color.parseColor("#F4F4F5"));
            } else {
                pinDots[i].setText("—");
                pinDots[i].setTextColor(Color.parseColor("#71717A"));
            }
        }
    }

    private void verify() {
        String pin = enteredPin.toString();
        if (mode == MODE_CREATE) {
            lockManager.setupInitialPin(pin);
            dismiss();
            if (valueListener != null) valueListener.onPinEntered(pin);
            if (listener != null) listener.onSuccess();
        } else if (mode == MODE_REMOVE) {
            if (lockManager.removePin(pin)) {
                dismiss();
                if (listener != null) listener.onSuccess();
            } else {
                shakeError();
                enteredPin.setLength(0);
                updateDots();
                checkLockout();
            }
        } else {
            // MODE_VERIFY
            if (!lockManager.hasPin()) {
                lockManager.setupInitialPin(pin);
                dismiss();
                if (listener != null) listener.onSuccess();
                return;
            }
            if (lockManager.verifyPin(pin)) {
                dismiss();
                if (listener != null) listener.onSuccess();
            } else {
                shakeError();
                enteredPin.setLength(0);
                updateDots();
                checkLockout();
            }
        }
    }

    private void shakeError() {
        if (dotsContainer != null) {
            ObjectAnimator shake = ObjectAnimator.ofFloat(dotsContainer, "translationX", 0f, 16f, -16f, 12f, -12f, 6f, -6f, 0f);
            shake.setDuration(350);
            shake.start();
        }
        if (errorTextView != null) {
            errorTextView.setVisibility(View.VISIBLE);
            errorTextView.setText("Mã PIN không đúng");
        }
    }

    private void checkLockout() {
        long sec = lockManager.getRemainingLockoutSeconds();
        if (sec > 0) {
            setKeypadEnabled(false);
            if (errorTextView != null) {
                errorTextView.setVisibility(View.VISIBLE);
                errorTextView.setText("Thử lại sau " + sec + " giây");
            }
            startCountdown();
        } else {
            setKeypadEnabled(true);
            if (errorTextView != null) errorTextView.setVisibility(View.INVISIBLE);
        }
    }

    private void startCountdown() {
        if (countdownRunnable != null) {
            timerHandler.removeCallbacks(countdownRunnable);
        }
        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                long remaining = lockManager.getRemainingLockoutSeconds();
                if (remaining > 0) {
                    if (errorTextView != null) {
                        errorTextView.setText("Thử lại sau " + remaining + " giây");
                    }
                    timerHandler.postDelayed(this, 1000);
                } else {
                    setKeypadEnabled(true);
                    if (errorTextView != null) {
                        errorTextView.setVisibility(View.INVISIBLE);
                    }
                    View key5 = findViewById(R.id.key5);
                    if (key5 != null) key5.requestFocus();
                }
            }
        };
        timerHandler.postDelayed(countdownRunnable, 1000);
    }

    private void setKeypadEnabled(boolean enabled) {
        int[] allIds = {
                R.id.key0, R.id.key1, R.id.key2, R.id.key3, R.id.key4,
                R.id.key5, R.id.key6, R.id.key7, R.id.key8, R.id.key9,
                R.id.keyClear
        };
        for (int id : allIds) {
            View v = findViewById(id);
            if (v != null) {
                v.setEnabled(enabled);
                v.setAlpha(enabled ? 1.0f : 0.35f);
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            int digit = keyCode - KeyEvent.KEYCODE_0;
            onDigitPressed(String.valueOf(digit));
            return true;
        } else if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
            int digit = keyCode - KeyEvent.KEYCODE_NUMPAD_0;
            onDigitPressed(String.valueOf(digit));
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DEL) {
            deleteDigit();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_BACK) {
            dismiss();
            if (listener != null) listener.onCancel();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (countdownRunnable != null) {
            timerHandler.removeCallbacks(countdownRunnable);
        }
    }
}
