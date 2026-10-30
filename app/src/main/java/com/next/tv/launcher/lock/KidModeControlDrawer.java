package com.next.tv.launcher.lock;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.next.tv.launcher.MainActivity;
import com.next.tv.launcher.R;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import com.next.tv.launcher.remote.QrCodeMatrixGenerator;
import com.next.tv.launcher.security.NativeSecurityBridge;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Modern Right-Sidebar Glass Drawer for Parent Kid Mode Control.
 * Manages Master Switch, Quota, Curfew, App Whitelist, PIN changes, and Remote Stream Monitoring.
 */
public final class KidModeControlDrawer {
    private static final String TAG = "TvHome.KidModeDrawer";

    private final Activity activity;
    private final Runnable onStateChanged;
    private final AppLockManager appLockManager;
    private final KidTimeQuotaTracker quotaTracker;

    private View drawerView;
    private View drawerContent;
    private boolean isShowing = false;
    private View triggerView;

    private TextView tvKidModeStatus;
    private Switch switchKidMode;
    private TextView tvDailyQuotaStatus;
    private TextView tvCurfewStatus;
    private View kidModeSubPanel;
    private LinearLayout containerKidSubPanelItems;
    private View cardKidMonitoringQrFloat;
    private ImageView imgKidMonitoringQrFloat;
    private TextView txtKidMonitoringUrlFloat;
    private boolean isSubPanelOpen = false;

    private TextView tvWhitelistStatus;
    private TextView tvChangePinStatus;

    public KidModeControlDrawer(Activity activity, Runnable onStateChanged) {
        this.activity = activity;
        this.onStateChanged = onStateChanged;
        this.appLockManager = AppLockManager.getInstance(activity);
        this.quotaTracker = KidTimeQuotaTracker.getInstance(activity);
    }

    public boolean isShowing() {
        return isShowing;
    }

    public void show() {
        show(null);
    }

    public void show(View trigger) {
        if (isShowing) return;
        this.triggerView = trigger != null ? trigger : activity.getCurrentFocus();

        LayoutInflater inflater = LayoutInflater.from(activity);
        drawerView = inflater.inflate(R.layout.drawer_kid_mode, null);
        drawerContent = drawerView.findViewById(R.id.kidModeContent);

        tvKidModeStatus = drawerView.findViewById(R.id.tvKidModeStatus);
        switchKidMode = drawerView.findViewById(R.id.switchKidMode);
        tvDailyQuotaStatus = drawerView.findViewById(R.id.tvDailyQuotaStatus);
        tvCurfewStatus = drawerView.findViewById(R.id.tvCurfewStatus);
        kidModeSubPanel = drawerView.findViewById(R.id.kidModeSubPanel);
        containerKidSubPanelItems = drawerView.findViewById(R.id.containerKidSubPanelItems);
        cardKidMonitoringQrFloat = drawerView.findViewById(R.id.cardKidMonitoringQrFloat);
        imgKidMonitoringQrFloat = drawerView.findViewById(R.id.imgKidMonitoringQrFloat);
        txtKidMonitoringUrlFloat = drawerView.findViewById(R.id.txtKidMonitoringUrlFloat);
        isSubPanelOpen = false;

        updateKidMonitoringQrUi(appLockManager.isKidModeActive());

        tvWhitelistStatus = drawerView.findViewById(R.id.tvWhitelistStatus);
        tvChangePinStatus = drawerView.findViewById(R.id.tvChangePinStatus);

        updateStatuses();

        View itemToggleKidMode = drawerView.findViewById(R.id.itemToggleKidMode);
        View itemDailyQuota = drawerView.findViewById(R.id.itemDailyQuota);
        View itemCurfew = drawerView.findViewById(R.id.itemCurfew);
        View itemAppWhitelist = drawerView.findViewById(R.id.itemAppWhitelist);
        View itemChangePin = drawerView.findViewById(R.id.itemChangePin);

        setupItemFocus(itemToggleKidMode);
        setupItemFocus(itemDailyQuota);
        setupItemFocus(itemCurfew);
        setupItemFocus(itemAppWhitelist);
        setupItemFocus(itemChangePin);

        View.OnKeyListener dismissOnBackOrLeft = (v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if (isSubPanelOpen) {
                        closeSubPanel();
                        return true;
                    }
                    dismiss();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (isSubPanelOpen) {
                        if (containerKidSubPanelItems != null && containerKidSubPanelItems.getChildCount() > 0) {
                            containerKidSubPanelItems.getChildAt(0).requestFocus();
                        }
                        return true;
                    }
                    dismiss();
                    return true;
                }
            }
            return false;
        };

        if (itemToggleKidMode != null) itemToggleKidMode.setOnKeyListener(dismissOnBackOrLeft);
        if (itemDailyQuota != null) itemDailyQuota.setOnKeyListener(dismissOnBackOrLeft);
        if (itemCurfew != null) itemCurfew.setOnKeyListener(dismissOnBackOrLeft);
        if (itemAppWhitelist != null) itemAppWhitelist.setOnKeyListener(dismissOnBackOrLeft);
        if (itemChangePin != null) itemChangePin.setOnKeyListener(dismissOnBackOrLeft);

        // 1. Toggle Kid Mode
        if (itemToggleKidMode != null) {
            itemToggleKidMode.setOnClickListener(v -> {
                boolean cur = appLockManager.isKidModeActive();
                boolean next = !cur;
                appLockManager.setKidModeActive(next);
                updateStatuses();
                updateKidMonitoringQrUi(next);
                if (onStateChanged != null) onStateChanged.run();
                String msg = next ? "Đã bật Chế độ trẻ em" : "Đã tắt Chế độ trẻ em";
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
            });
        }

        // 2. Change Daily Quota
        if (itemDailyQuota != null) {
            itemDailyQuota.setOnClickListener(v -> {
                int quota = quotaTracker.getDailyQuotaMinutes();
                int nextQuota;
                if (quota == 0) nextQuota = 30;
                else if (quota == 30) nextQuota = 60;
                else if (quota == 60) nextQuota = 120;
                else nextQuota = 0;

                quotaTracker.setDailyQuotaMinutes(nextQuota);
                updateStatuses();
                String msg = (nextQuota == 0) ? "Không giới hạn" : ("Hạn mức: " + nextQuota + " phút");
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
            });
        }

        // 3. Curfew sleep schedule
        if (itemCurfew != null) {
            itemCurfew.setOnClickListener(v -> {
                boolean cur = quotaTracker.isCurfewEnabled();
                quotaTracker.setCurfewEnabled(!cur);
                updateStatuses();
                String msg = !cur ? "Đã bật giới nghiêm" : "Đã tắt giới nghiêm";
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
            });
        }

        // 4. App Whitelist Selection Dialog
        if (itemAppWhitelist != null) {
            itemAppWhitelist.setOnClickListener(v -> openWhitelistSubPanel());
        }

        // 5. Change Parent PIN
        if (itemChangePin != null) {
            itemChangePin.setOnClickListener(v -> {
                PinLockDialog pinDialog = new PinLockDialog(activity, PinLockDialog.MODE_CREATE,
                        "Đổi mã PIN", "Nhập 4 số bí mật",
                        new PinLockDialog.PinResultListener() {
                            @Override
                            public void onSuccess() {
                                Toast.makeText(activity, "Đã cập nhật mã PIN", Toast.LENGTH_SHORT).show();
                                updateStatuses();
                            }

                            @Override
                            public void onCancel() {}
                        });
                pinDialog.show();
            });
        }

        activity.addContentView(drawerView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (activity instanceof MainActivity) {
            ((MainActivity) activity).blockMainContentFocus();
        }

        isShowing = true;
        if (drawerContent != null) {
            drawerContent.setTranslationX(400f);
            drawerContent.animate().translationX(0f).setDuration(220).start();
        }

        if (itemToggleKidMode != null) {
            itemToggleKidMode.post(() -> itemToggleKidMode.requestFocus());
        }
    }

    private void setupItemFocus(View v) {
        if (v == null) return;
        v.setOnFocusChangeListener((view, hasFocus) -> {
            view.animate().scaleX(hasFocus ? 1.03f : 1.0f).scaleY(hasFocus ? 1.03f : 1.0f).setDuration(140).start();
            if (view instanceof ViewGroup) {
                updateChildColors((ViewGroup) view, hasFocus);
            }
            if (hasFocus && isSubPanelOpen) {
                closeSubPanel();
            }
        });
    }

    private void updateChildColors(ViewGroup group, boolean hasFocus) {
        int count = group.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = group.getChildAt(i);
            if (child instanceof TextView) {
                TextView tv = (TextView) child;
                if (tv.getId() == R.id.tvKidModeStatus || tv.getId() == R.id.tvDailyQuotaStatus
                        || tv.getId() == R.id.tvCurfewStatus || tv.getId() == R.id.tvWhitelistStatus
                        || tv.getId() == R.id.tvChangePinStatus) {
                    tv.setTextColor(Color.parseColor(hasFocus ? "#52525B" : "#A1A1AA"));
                } else {
                    tv.setTextColor(Color.parseColor(hasFocus ? "#09090B" : "#F4F4F5"));
                }
            } else if (child instanceof ImageView) {
                ((ImageView) child).setColorFilter(Color.parseColor(hasFocus ? "#09090B" : "#E4E4E7"));
            } else if (child instanceof ViewGroup) {
                updateChildColors((ViewGroup) child, hasFocus);
            }
        }
    }

    private void updateStatuses() {
        boolean active = appLockManager.isKidModeActive();
        if (tvKidModeStatus != null) {
            tvKidModeStatus.setText(active ? "Đang bật" : "Đang tắt");
        }
        if (switchKidMode != null) {
            switchKidMode.setChecked(active);
        }

        if (tvDailyQuotaStatus != null) {
            int quota = quotaTracker.getDailyQuotaMinutes();
            tvDailyQuotaStatus.setText(quota == 0 ? "Không giới hạn" : (quota + " phút mỗi ngày"));
        }

        if (tvCurfewStatus != null) {
            boolean curfew = quotaTracker.isCurfewEnabled();
            tvCurfewStatus.setText(curfew ? "21:00 đến 06:30" : "Đang tắt");
        }

        if (tvWhitelistStatus != null) {
            int count = appLockManager.getKidWhitelistPackages().size();
            tvWhitelistStatus.setText(count > 0 ? (count + " ứng dụng cho phép") : "Chưa chọn ứng dụng");
        }

        if (tvChangePinStatus != null) {
            tvChangePinStatus.setText(appLockManager.hasPin() ? "Đã đặt mã PIN" : "Chưa cài mã PIN");
        }
    }

    private void updateKidMonitoringQrUi(boolean show) {
        if (cardKidMonitoringQrFloat == null) return;
        if (show) {
            String ip = getLocalIpAddress();
            String baseUrl;
            try {
                baseUrl = NativeSecurityBridge.getProxyBaseUrl(activity);
            } catch (Throwable t) {
                baseUrl = null;
            }
            if (baseUrl == null || baseUrl.isEmpty()) {
                baseUrl = "https://api.example.com";
            }
            String url = baseUrl + "/remote/stream?device=" + ip;
            if (txtKidMonitoringUrlFloat != null) txtKidMonitoringUrlFloat.setText(url);
            Bitmap qr = QrCodeMatrixGenerator.generateQrBitmap(url, 440);
            if (qr != null && imgKidMonitoringQrFloat != null) {
                imgKidMonitoringQrFloat.setImageBitmap(qr);
            }
            if (cardKidMonitoringQrFloat.getVisibility() != View.VISIBLE) {
                cardKidMonitoringQrFloat.setVisibility(View.VISIBLE);
                cardKidMonitoringQrFloat.setAlpha(0f);
                cardKidMonitoringQrFloat.setScaleX(0.92f);
                cardKidMonitoringQrFloat.setScaleY(0.92f);
                cardKidMonitoringQrFloat.animate().alpha(1f).scaleX(1.0f).scaleY(1.0f).setDuration(220).start();
            }
        } else {
            if (cardKidMonitoringQrFloat.getVisibility() == View.VISIBLE) {
                cardKidMonitoringQrFloat.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(160)
                        .withEndAction(() -> cardKidMonitoringQrFloat.setVisibility(View.GONE)).start();
            }
        }
    }

    private void openWhitelistSubPanel() {
        if (kidModeSubPanel == null || containerKidSubPanelItems == null) return;
        isSubPanelOpen = true;

        if (cardKidMonitoringQrFloat != null && cardKidMonitoringQrFloat.getVisibility() == View.VISIBLE) {
            cardKidMonitoringQrFloat.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(150)
                    .withEndAction(() -> cardKidMonitoringQrFloat.setVisibility(View.GONE)).start();
        }

        containerKidSubPanelItems.removeAllViews();
        PackageManager pm = activity.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        List<ApplicationInfo> userApps = new ArrayList<>();
        String myPkg = activity.getPackageName();

        for (ApplicationInfo ai : apps) {
            if (ai.packageName.equals(myPkg)) continue;
            boolean isSystem = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
            if (!isSystem && pm.getLaunchIntentForPackage(ai.packageName) != null) {
                userApps.add(ai);
            }
        }
        Collections.sort(userApps, (a, b) -> pm.getApplicationLabel(a).toString().compareToIgnoreCase(pm.getApplicationLabel(b).toString()));

        float density = activity.getResources().getDisplayMetrics().density;
        for (ApplicationInfo ai : userApps) {
            String label = pm.getApplicationLabel(ai).toString();
            Drawable icon = pm.getApplicationIcon(ai);
            boolean isWhitelisted = appLockManager.isWhitelistedInKidMode(ai.packageName);

            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding((int) (14 * density), (int) (10 * density), (int) (14 * density), (int) (10 * density));
            row.setBackgroundResource(R.drawable.drawer_menu_item_bg);
            row.setFocusable(true);
            row.setClickable(true);

            ImageView iv = new ImageView(activity);
            iv.setImageDrawable(icon);
            iv.setLayoutParams(new LinearLayout.LayoutParams((int) (28 * density), (int) (28 * density)));
            iv.setPadding(0, 0, (int) (10 * density), 0);
            row.addView(iv);

            TextView tv = new TextView(activity);
            tv.setText(label);
            tv.setTextColor(Color.parseColor(isWhitelisted ? "#10B981" : "#F4F4F5"));
            tv.setTextSize(14f);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(tv);

            Switch sw = new Switch(activity);
            sw.setChecked(isWhitelisted);
            sw.setFocusable(false);
            sw.setClickable(false);
            row.addView(sw);

            row.setOnFocusChangeListener((v, hasFocus) -> {
                v.animate().scaleX(hasFocus ? 1.02f : 1.0f).scaleY(hasFocus ? 1.02f : 1.0f).setDuration(120).start();
                tv.setTextColor(Color.parseColor(hasFocus ? "#09090B" : (sw.isChecked() ? "#10B981" : "#F4F4F5")));
            });

            row.setOnClickListener(v -> {
                boolean cur = sw.isChecked();
                sw.setChecked(!cur);
                appLockManager.setAppWhitelistedInKidMode(ai.packageName, !cur);
                tv.setTextColor(Color.parseColor(row.hasFocus() ? "#09090B" : (!cur ? "#10B981" : "#F4F4F5")));
                updateStatuses();
            });

            row.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        closeSubPanel();
                        return true;
                    }
                }
                return false;
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int) (6 * density);
            row.setLayoutParams(lp);

            containerKidSubPanelItems.addView(row);
        }

        kidModeSubPanel.setVisibility(View.VISIBLE);
        int shift = kidModeSubPanel.getWidth() > 0 ? kidModeSubPanel.getWidth() : (int) (380 * density);
        kidModeSubPanel.setTranslationX(shift);
        kidModeSubPanel.animate().translationX(0f).setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    if (containerKidSubPanelItems.getChildCount() > 0) {
                        containerKidSubPanelItems.getChildAt(0).requestFocus();
                    }
                }).start();
    }

    public void closeSubPanel() {
        if (!isSubPanelOpen || kidModeSubPanel == null) return;
        isSubPanelOpen = false;
        float density = activity.getResources().getDisplayMetrics().density;
        int shift = kidModeSubPanel.getWidth() > 0 ? kidModeSubPanel.getWidth() : (int) (380 * density);
        kidModeSubPanel.animate().translationX(shift).setDuration(180)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    kidModeSubPanel.setVisibility(View.GONE);
                    if (appLockManager.isKidModeActive()) {
                        updateKidMonitoringQrUi(true);
                    }
                    View currentFocus = activity.getCurrentFocus();
                    boolean isFocusInMain = currentFocus != null && drawerContent != null && isDescendantOf(drawerContent, currentFocus);
                    if (!isFocusInMain) {
                        View itemAppWhitelist = drawerView != null ? drawerView.findViewById(R.id.itemAppWhitelist) : null;
                        if (itemAppWhitelist != null) {
                            itemAppWhitelist.requestFocus();
                        }
                    }
                }).start();
    }

    private boolean isDescendantOf(View parent, View child) {
        if (child == null || parent == null) return false;
        if (child == parent) return true;
        android.view.ViewParent p = child.getParent();
        while (p instanceof View) {
            if (p == parent) return true;
            p = p.getParent();
        }
        return false;
    }

    private void showAppWhitelistDialog() {
        PackageManager pm = activity.getPackageManager();
        Intent leanbackIntent = new Intent(Intent.ACTION_MAIN);
        leanbackIntent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> leanbackApps = pm.queryIntentActivities(leanbackIntent, 0);

        Intent standardIntent = new Intent(Intent.ACTION_MAIN);
        standardIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> standardApps = pm.queryIntentActivities(standardIntent, 0);

        Set<String> seen = new HashSet<>();
        List<ApplicationInfo> apps = new ArrayList<>();
        String myPkg = activity.getPackageName();

        if (leanbackApps != null) {
            for (ResolveInfo ri : leanbackApps) {
                if (ri.activityInfo != null && ri.activityInfo.applicationInfo != null) {
                    String pkg = ri.activityInfo.packageName;
                    if (!pkg.equals(myPkg) && seen.add(pkg)) {
                        apps.add(ri.activityInfo.applicationInfo);
                    }
                }
            }
        }
        if (standardApps != null) {
            for (ResolveInfo ri : standardApps) {
                if (ri.activityInfo != null && ri.activityInfo.applicationInfo != null) {
                    String pkg = ri.activityInfo.packageName;
                    if (!pkg.equals(myPkg) && seen.add(pkg)) {
                        apps.add(ri.activityInfo.applicationInfo);
                    }
                }
            }
        }

        Collections.sort(apps, (a, b) -> pm.getApplicationLabel(a).toString().compareToIgnoreCase(pm.getApplicationLabel(b).toString()));

        Dialog dialog = new Dialog(activity, R.style.Theme_TvHome);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.8f);
        }

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.glass_panel);
        float density = activity.getResources().getDisplayMetrics().density;
        int pad = (int) (20 * density);
        root.setPadding(pad, pad, pad, pad);
        root.setLayoutParams(new ViewGroup.LayoutParams((int) (480 * density), (int) (520 * density)));

        TextView title = new TextView(activity);
        title.setText("Ứng dụng an toàn cho trẻ");
        title.setTextColor(Color.parseColor("#F4F4F5"));
        title.setTextSize(17f);
        title.setPadding(0, 0, 0, (int) (12 * density));
        root.addView(title);

        ScrollView scroll = new ScrollView(activity);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout appList = new LinearLayout(activity);
        appList.setOrientation(LinearLayout.VERTICAL);

        for (ApplicationInfo ai : apps) {
            String label = pm.getApplicationLabel(ai).toString();
            Drawable icon = pm.getApplicationIcon(ai);
            boolean isWhitelisted = appLockManager.isWhitelistedInKidMode(ai.packageName);

            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding((int) (12 * density), (int) (8 * density), (int) (12 * density), (int) (8 * density));
            row.setBackgroundResource(R.drawable.drawer_menu_item_bg);
            row.setFocusable(true);
            row.setClickable(true);

            ImageView iv = new ImageView(activity);
            iv.setImageDrawable(icon);
            iv.setLayoutParams(new LinearLayout.LayoutParams((int) (32 * density), (int) (32 * density)));
            iv.setPadding(0, 0, (int) (10 * density), 0);
            row.addView(iv);

            TextView tv = new TextView(activity);
            tv.setText(label);
            tv.setTextColor(Color.parseColor(isWhitelisted ? "#10B981" : "#F4F4F5"));
            tv.setTextSize(14f);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(tv);

            Switch sw = new Switch(activity);
            sw.setChecked(isWhitelisted);
            sw.setFocusable(false);
            sw.setClickable(false);
            row.addView(sw);

            row.setOnFocusChangeListener((v, hasFocus) -> {
                v.animate().scaleX(hasFocus ? 1.02f : 1.0f).scaleY(hasFocus ? 1.02f : 1.0f).setDuration(120).start();
                tv.setTextColor(Color.parseColor(hasFocus ? "#09090B" : sw.isChecked() ? "#10B981" : "#F4F4F5"));
            });

            row.setOnClickListener(v -> {
                boolean cur = sw.isChecked();
                sw.setChecked(!cur);
                appLockManager.setAppWhitelistedInKidMode(ai.packageName, !cur);
                tv.setTextColor(Color.parseColor(row.hasFocus() ? "#09090B" : (!cur ? "#10B981" : "#F4F4F5")));
                updateStatuses();
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int) (6 * density);
            row.setLayoutParams(lp);

            appList.addView(row);
        }

        scroll.addView(appList);
        root.addView(scroll);

        Button btnDone = new Button(activity);
        btnDone.setText("Hoàn tất");
        btnDone.setTextColor(Color.parseColor("#09090B"));
        btnDone.setBackgroundResource(R.drawable.search_pill_btn_bg);
        btnDone.setFocusable(true);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (42 * density));
        btnLp.topMargin = (int) (12 * density);
        btnDone.setLayoutParams(btnLp);
        btnDone.setOnClickListener(v -> {
            dialog.dismiss();
            if (onStateChanged != null) onStateChanged.run();
        });
        root.addView(btnDone);

        dialog.setContentView(root);
        dialog.show();
        btnDone.requestFocus();
    }



    private static String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr.getAddress().length == 4) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    public void dismiss() {
        if (!isShowing || drawerView == null) return;

        if (activity instanceof MainActivity) {
            ((MainActivity) activity).unblockMainContentFocus();
        }

        if (triggerView != null && triggerView.isShown()) {
            triggerView.post(() -> triggerView.requestFocus());
        }

        if (drawerContent != null) {
            drawerContent.animate().translationX(400f).setDuration(180).withEndAction(() -> {
                try {
                    ViewGroup parent = (ViewGroup) drawerView.getParent();
                    if (parent != null) parent.removeView(drawerView);
                } catch (Exception ignored) {}
                drawerView = null;
                isShowing = false;
            }).start();
        } else {
            try {
                ViewGroup parent = (ViewGroup) drawerView.getParent();
                if (parent != null) parent.removeView(drawerView);
            } catch (Exception ignored) {}
            drawerView = null;
            isShowing = false;
        }
    }
}
