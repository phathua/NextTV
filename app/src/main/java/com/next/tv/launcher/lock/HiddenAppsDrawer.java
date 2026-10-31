package com.next.tv.launcher.lock;

import com.next.tv.launcher.utility.UtilitySectionAdapter;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.next.tv.launcher.MainActivity;
import com.next.tv.launcher.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Modern Right-Sidebar Drawer for Hiding/Showing Installed Apps on TV Home.
 * Features:
 * - Smart Categorization Tabs: Tất cả, Giải trí, Công cụ, Hệ thống
 * - Full App Icons, titles, and package subtitles
 * - Instant toggle switch with live state persistence
 * - Inverted Apple tvOS Focus state with zero clipping
 */
public final class HiddenAppsDrawer {
    private final Activity activity;
    private final Runnable onAppsChanged;
    private final AppLockManager appLockManager;

    private View drawerView;
    private View drawerContent;
    private boolean isShowing = false;
    private View previousFocusView;

    private LinearLayout rowCategoryTabs;
    private LinearLayout containerAppRows;
    private ScrollView scrollHiddenApps;

    private String currentCategory = "ALL";
    private final List<AppItem> allAppItems = new ArrayList<>();
    private final List<TextView> tabViews = new ArrayList<>();

    private static final class AppItem {
        final ApplicationInfo info;
        final String label;
        final String category; // "ALL", "MEDIA", "TOOLS", "SYSTEM"
        final Drawable icon;
        boolean isVisible;

        AppItem(ApplicationInfo info, String label, String category, Drawable icon, boolean isVisible) {
            this.info = info;
            this.label = label;
            this.category = category;
            this.icon = icon;
            this.isVisible = isVisible;
        }
    }

    private static final class CategoryDef {
        final String id;
        final String title;

        CategoryDef(String id, String title) {
            this.id = id;
            this.title = title;
        }
    }

    private static final CategoryDef[] CATEGORIES = new CategoryDef[]{
            new CategoryDef("ALL", "Tất cả"),
            new CategoryDef("MEDIA", "Giải trí"),
            new CategoryDef("TOOLS", "Công cụ"),
            new CategoryDef("SYSTEM", "Hệ thống")
    };

    public HiddenAppsDrawer(Activity activity, Runnable onAppsChanged) {
        this.activity = activity;
        this.onAppsChanged = onAppsChanged;
        this.appLockManager = AppLockManager.getInstance(activity);
    }

    public boolean isShowing() {
        return isShowing;
    }

    public void show() {
        show(null);
    }

    public void show(View triggerView) {
        if (isShowing && drawerView != null && drawerView.getParent() != null) {
            if (containerAppRows != null && containerAppRows.getChildCount() > 0) {
                containerAppRows.getChildAt(0).requestFocus();
            }
            return;
        }
        previousFocusView = triggerView != null ? triggerView : activity.getCurrentFocus();
        isShowing = false;

        LayoutInflater inflater = LayoutInflater.from(activity);
        drawerView = inflater.inflate(R.layout.drawer_hidden_apps, null);
        drawerContent = drawerView.findViewById(R.id.hiddenAppsContent);
        rowCategoryTabs = drawerView.findViewById(R.id.rowCategoryTabs);
        containerAppRows = drawerView.findViewById(R.id.containerAppRows);
        scrollHiddenApps = drawerView.findViewById(R.id.scrollHiddenApps);

        if (activity instanceof MainActivity) {
            ((MainActivity) activity).blockMainContentFocus();
        }

        activity.addContentView(drawerView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        isShowing = true;

        if (drawerContent != null) {
            drawerContent.setTranslationX(400f);
            drawerContent.animate().translationX(0f).setDuration(220).start();
        }

        View root = drawerView.findViewById(R.id.hiddenAppsRoot);
        if (root != null) {
            root.setOnClickListener(v -> dismiss());
        }

        loadAppsAndSetup();
    }

    public void dismiss() {
        if (!isShowing || drawerView == null) return;
        isShowing = false;

        if (activity instanceof MainActivity) {
            ((MainActivity) activity).unblockMainContentFocus();
        }

        View target = previousFocusView;
        if (target != null && target.isShown()) {
            target.requestFocus();
            target.post(() -> {
                target.requestFocus();
                if (activity instanceof MainActivity) {
                    ((MainActivity) activity).focusGlassOnCard(target);
                }
            });
        } else if (activity instanceof MainActivity) {
            ((MainActivity) activity).focusUtilityType(com.next.tv.launcher.utility.UtilitySectionAdapter.TYPE_HIDE_APPS);
        }

        if (drawerContent != null) {
            drawerContent.animate().translationX(400f).setDuration(180).withEndAction(() -> {
                if (drawerView != null && drawerView.getParent() instanceof ViewGroup) {
                    ((ViewGroup) drawerView.getParent()).removeView(drawerView);
                    drawerView = null;
                }
            }).start();
        } else {
            if (drawerView.getParent() instanceof ViewGroup) {
                ((ViewGroup) drawerView.getParent()).removeView(drawerView);
                drawerView = null;
            }
        }

        if (onAppsChanged != null) {
            onAppsChanged.run();
        }
    }

    private void loadAppsAndSetup() {
        PackageManager pm = activity.getPackageManager();
        String myPkg = activity.getPackageName();

        // Query only launchable intents (Leanback ATV + Standard Phone launcher) matching Home screen discovery
        Intent leanbackIntent = new Intent(Intent.ACTION_MAIN);
        leanbackIntent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> leanbackApps = pm.queryIntentActivities(leanbackIntent, 0);

        Intent standardIntent = new Intent(Intent.ACTION_MAIN);
        standardIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> standardApps = pm.queryIntentActivities(standardIntent, 0);

        Set<String> launchablePackages = new HashSet<>();
        List<ApplicationInfo> launchableAppInfos = new ArrayList<>();

        if (leanbackApps != null) {
            for (ResolveInfo ri : leanbackApps) {
                if (ri.activityInfo != null && ri.activityInfo.applicationInfo != null) {
                    String pkg = ri.activityInfo.packageName;
                    if (!pkg.equals(myPkg) && launchablePackages.add(pkg)) {
                        launchableAppInfos.add(ri.activityInfo.applicationInfo);
                    }
                }
            }
        }

        if (standardApps != null) {
            for (ResolveInfo ri : standardApps) {
                if (ri.activityInfo != null && ri.activityInfo.applicationInfo != null) {
                    String pkg = ri.activityInfo.packageName;
                    if (!pkg.equals(myPkg) && launchablePackages.add(pkg)) {
                        launchableAppInfos.add(ri.activityInfo.applicationInfo);
                    }
                }
            }
        }

        allAppItems.clear();
        for (ApplicationInfo ai : launchableAppInfos) {
            String label = pm.getApplicationLabel(ai).toString();
            Drawable icon = pm.getApplicationIcon(ai);
            String category = detectCategory(ai, label);
            boolean isVisible = !appLockManager.isCasualHidden(ai.packageName);

            allAppItems.add(new AppItem(ai, label, category, icon, isVisible));
        }

        Collections.sort(allAppItems, (a, b) -> a.label.compareToIgnoreCase(b.label));

        buildCategoryTabs();
        renderFilteredApps();

        drawerView.postDelayed(() -> {
            if (containerAppRows != null && containerAppRows.getChildCount() > 0) {
                containerAppRows.getChildAt(0).requestFocus();
            }
        }, 120);
    }

    private static String detectCategory(ApplicationInfo ai, String label) {
        String pkg = ai.packageName.toLowerCase(Locale.ROOT);
        String name = label.toLowerCase(Locale.ROOT);

        boolean isSystemApp = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;

        // Media / Entertainment
        if (pkg.contains("youtube") || pkg.contains("netflix") || pkg.contains("stremio")
                || pkg.contains("vtv") || pkg.contains("kodi") || pkg.contains("spotify")
                || pkg.contains("mytv") || pkg.contains("tv360") || pkg.contains("fptplay")
                || pkg.contains("xemtv") || pkg.contains("sctv") || pkg.contains("vieon")
                || pkg.contains("zing") || pkg.contains("cloudstream") || pkg.contains("smarttube")
                || name.contains("truyền hình") || name.contains("tivi") || name.contains("phim")
                || name.contains("nhạc") || name.contains("media") || name.contains("video")) {
            return "MEDIA";
        }

        // Tools / Utilities
        if (pkg.contains("file") || pkg.contains("browser") || pkg.contains("chrome")
                || pkg.contains("firefox") || pkg.contains("clean") || pkg.contains("speed")
                || pkg.contains("x-plore") || pkg.contains("smartkit") || pkg.contains("manager")
                || pkg.contains("terminal") || pkg.contains("downloader") || pkg.contains("cast")
                || name.contains("tệp") || name.contains("duyệt") || name.contains("dọn dẹp")
                || name.contains("công cụ") || name.contains("quản lý")) {
            return "TOOLS";
        }

        // System
        if (isSystemApp || pkg.contains("android") || pkg.contains("google")
                || pkg.contains("vending") || pkg.contains("settings") || pkg.contains("systemui")
                || pkg.contains("droidlogic") || pkg.contains("cvte") || pkg.contains("mediatek")
                || name.contains("cài đặt") || name.contains("dịch vụ")) {
            return "SYSTEM";
        }

        return "TOOLS";
    }

    private void buildCategoryTabs() {
        if (rowCategoryTabs == null) return;
        rowCategoryTabs.removeAllViews();
        tabViews.clear();

        int padH = (int) (12 * activity.getResources().getDisplayMetrics().density);
        int padV = (int) (6 * activity.getResources().getDisplayMetrics().density);
        int marginEnd = (int) (8 * activity.getResources().getDisplayMetrics().density);

        for (CategoryDef cat : CATEGORIES) {
            TextView tab = new TextView(activity);
            tab.setText(cat.title);
            tab.setTextSize(12.5f);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(padH, padV, padH, padV);
            tab.setFocusable(true);
            tab.setClickable(true);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(marginEnd);
            tab.setLayoutParams(lp);

            updateTabStyle(tab, cat.id.equals(currentCategory), false);

            tab.setOnFocusChangeListener((v, hasFocus) -> {
                updateTabStyle(tab, cat.id.equals(currentCategory), hasFocus);
                if (hasFocus && !cat.id.equals(currentCategory)) {
                    currentCategory = cat.id;
                    refreshAllTabStyles();
                    renderFilteredApps();
                }
            });

            tab.setOnClickListener(v -> {
                if (!cat.id.equals(currentCategory)) {
                    currentCategory = cat.id;
                    refreshAllTabStyles();
                    renderFilteredApps();
                }
            });

            tab.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        dismiss();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && tabViews.indexOf(v) == 0) {
                        dismiss();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (containerAppRows != null && containerAppRows.getChildCount() > 0) {
                            containerAppRows.getChildAt(0).requestFocus();
                            return true;
                        }
                    }
                }
                return false;
            });

            rowCategoryTabs.addView(tab);
            tabViews.add(tab);
        }
    }

    private void refreshAllTabStyles() {
        for (int i = 0; i < CATEGORIES.length && i < tabViews.size(); i++) {
            CategoryDef cat = CATEGORIES[i];
            TextView tv = tabViews.get(i);
            updateTabStyle(tv, cat.id.equals(currentCategory), tv.hasFocus());
        }
    }

    private void updateTabStyle(TextView tab, boolean isSelected, boolean isFocused) {
        float density = activity.getResources().getDisplayMetrics().density;
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(8 * density);

        if (isFocused) {
            bg.setColor(Color.parseColor("#F4F4F5"));
            bg.setStroke((int) (1 * density), Color.parseColor("#FFFFFF"));
            tab.setTextColor(Color.parseColor("#09090B"));
        } else if (isSelected) {
            bg.setColor(Color.parseColor("#27272A"));
            bg.setStroke((int) (1 * density), Color.parseColor("#3F3F46"));
            tab.setTextColor(Color.parseColor("#10B981"));
        } else {
            bg.setColor(Color.parseColor("#14FFFFFF"));
            bg.setStroke((int) (1 * density), Color.parseColor("#27272A"));
            tab.setTextColor(Color.parseColor("#A1A1AA"));
        }

        tab.setBackground(bg);
    }

    private void renderFilteredApps() {
        if (containerAppRows == null) return;
        containerAppRows.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(activity);

        for (AppItem item : allAppItems) {
            if (!"ALL".equals(currentCategory) && !currentCategory.equals(item.category)) {
                continue;
            }

            View row = inflater.inflate(R.layout.item_hidden_app_row, containerAppRows, false);
            ImageView imgIcon = row.findViewById(R.id.imgAppRowIcon);
            TextView txtTitle = row.findViewById(R.id.txtAppRowTitle);
            TextView txtSub = row.findViewById(R.id.txtAppRowSub);
            Switch switchToggle = row.findViewById(R.id.switchAppRow);

            imgIcon.setImageDrawable(item.icon);
            txtTitle.setText(item.label);
            txtSub.setText(item.info.packageName);
            switchToggle.setChecked(item.isVisible);

            // Inverted focus handling
            row.setOnFocusChangeListener((v, hasFocus) -> {
                v.animate().scaleX(hasFocus ? 1.01f : 1.0f).scaleY(hasFocus ? 1.02f : 1.0f).setDuration(120).start();
                txtTitle.setTextColor(Color.parseColor(hasFocus ? "#09090B" : "#FFFFFFFF"));
                txtSub.setTextColor(Color.parseColor(hasFocus ? "#52525B" : "#71717A"));
            });

            // Toggle visibility on click
            row.setOnClickListener(v -> {
                item.isVisible = !item.isVisible;
                switchToggle.setChecked(item.isVisible);
                appLockManager.setCasualHidden(item.info.packageName, !item.isVisible);
                String msg = item.isVisible ? "Đã hiện ứng dụng" : "Đã ẩn ứng dụng";
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
            });

            // Navigation
            row.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        dismiss();
                        return true;
                    }
                }
                return false;
            });

            containerAppRows.addView(row);
        }
    }
}
