package com.next.tv.launcher.clean;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.next.tv.launcher.MainActivity;
import com.next.tv.launcher.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Advanced Memory Cleaner & Smart RAM Drawer with Multi-Segment Donut Chart.
 */
public final class MemoryCleanerDrawer {

    private static final String TAG = "TvHome.SmartRAM";

    private final Activity activity;
    private final SmartRamConfigManager config;
    private final LocalAdbBridge adbBridge;

    private View drawerView;
    private View drawerContent;
    private View containerDonutCard;
    private RamDonutChartView donutChartView;
    private LinearLayout containerLegends;

    private View memorySubPanel;
    private TextView tvMemorySubPanelTitle;
    private LinearLayout containerMemorySubPanelItems;
    private TextView tabCategoryAll;
    private TextView tabCategoryMedia;
    private TextView tabCategoryTools;

    private TextView tvQuickCleanResult;
    private TextView tvMemoryModeStatus;
    private TextView tvModeDescriptionBanner;
    private TextView tvMaxCachedStatus;
    private TextView tvCronIntervalStatus;
    private TextView tvOverloadThresholdStatus;
    private TextView tvRecentGraceStatus;
    private TextView tvProtectMediaStatus;
    private TextView tvWhitelistCountStatus;

    private View btnQuickSmartClean;
    private View itemMemoryMode;
    private View itemMaxCached;
    private View itemCronInterval;
    private View itemOverloadThreshold;
    private View itemRecentGrace;
    private View itemProtectMedia;
    private View itemManageWhitelist;

    private boolean isShowing = false;
    private boolean isSubPanelOpen = false;
    private View triggerView;

    private String currentSubPanelFilter = "all"; // "all", "media", "tools"
    private List<ApplicationInfo> cachedUserApps = new ArrayList<>();

    public MemoryCleanerDrawer(Activity activity) {
        this.activity = activity;
        this.config = SmartRamConfigManager.getInstance(activity);
        this.adbBridge = LocalAdbBridge.getInstance(activity);
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
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        drawerView = inflater.inflate(R.layout.drawer_memory_cleaner, decor, false);

        drawerContent = drawerView.findViewById(R.id.memoryDrawerContent);
        containerDonutCard = drawerView.findViewById(R.id.containerDonutCard);
        donutChartView = drawerView.findViewById(R.id.donutChartView);
        containerLegends = drawerView.findViewById(R.id.containerLegends);

        memorySubPanel = drawerView.findViewById(R.id.memorySubPanel);
        tvMemorySubPanelTitle = drawerView.findViewById(R.id.tvMemorySubPanelTitle);
        containerMemorySubPanelItems = drawerView.findViewById(R.id.containerMemorySubPanelItems);
        tabCategoryAll = drawerView.findViewById(R.id.tabCategoryAll);
        tabCategoryMedia = drawerView.findViewById(R.id.tabCategoryMedia);
        tabCategoryTools = drawerView.findViewById(R.id.tabCategoryTools);

        tvQuickCleanResult = drawerView.findViewById(R.id.tvQuickCleanResult);
        tvMemoryModeStatus = drawerView.findViewById(R.id.tvMemoryModeStatus);
        tvModeDescriptionBanner = drawerView.findViewById(R.id.tvModeDescriptionBanner);
        tvMaxCachedStatus = drawerView.findViewById(R.id.tvMaxCachedStatus);
        tvCronIntervalStatus = drawerView.findViewById(R.id.tvCronIntervalStatus);
        tvOverloadThresholdStatus = drawerView.findViewById(R.id.tvOverloadThresholdStatus);
        tvRecentGraceStatus = drawerView.findViewById(R.id.tvRecentGraceStatus);
        tvProtectMediaStatus = drawerView.findViewById(R.id.tvProtectMediaStatus);
        tvWhitelistCountStatus = drawerView.findViewById(R.id.tvWhitelistCountStatus);

        btnQuickSmartClean = drawerView.findViewById(R.id.btnQuickSmartClean);
        itemMemoryMode = drawerView.findViewById(R.id.itemMemoryMode);
        itemMaxCached = drawerView.findViewById(R.id.itemMaxCached);
        itemCronInterval = drawerView.findViewById(R.id.itemCronInterval);
        itemOverloadThreshold = drawerView.findViewById(R.id.itemOverloadThreshold);
        itemRecentGrace = drawerView.findViewById(R.id.itemRecentGrace);
        itemProtectMedia = drawerView.findViewById(R.id.itemProtectMedia);
        itemManageWhitelist = drawerView.findViewById(R.id.itemManageWhitelist);

        setupSidebarItemFocus(btnQuickSmartClean);
        setupSidebarItemFocus(itemMemoryMode);
        setupSidebarItemFocus(itemMaxCached);
        setupSidebarItemFocus(itemCronInterval);
        setupSidebarItemFocus(itemOverloadThreshold);
        setupSidebarItemFocus(itemRecentGrace);
        setupSidebarItemFocus(itemProtectMedia);
        setupSidebarItemFocus(itemManageWhitelist);

        setupItemFocusAnim(tabCategoryAll);
        setupItemFocusAnim(tabCategoryMedia);
        setupItemFocusAnim(tabCategoryTools);

        View.OnKeyListener masterItemKeyListener = (v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                    if (isSubPanelOpen) {
                        closeSubPanel();
                        return true;
                    }
                    dismiss();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (isSubPanelOpen) {
                        if (containerMemorySubPanelItems != null && containerMemorySubPanelItems.getChildCount() > 0) {
                            View firstRow = containerMemorySubPanelItems.getChildAt(0);
                            if (firstRow instanceof ViewGroup && ((ViewGroup) firstRow).getChildCount() > 0) {
                                ((ViewGroup) firstRow).getChildAt(0).requestFocus();
                            } else if (firstRow != null) {
                                firstRow.requestFocus();
                            }
                            return true;
                        }
                    }
                    dismiss();
                    return true;
                }
            }
            return false;
        };

        btnQuickSmartClean.setOnKeyListener(masterItemKeyListener);
        itemMemoryMode.setOnKeyListener(masterItemKeyListener);
        itemMaxCached.setOnKeyListener(masterItemKeyListener);
        itemCronInterval.setOnKeyListener(masterItemKeyListener);
        itemOverloadThreshold.setOnKeyListener(masterItemKeyListener);
        itemRecentGrace.setOnKeyListener(masterItemKeyListener);
        itemProtectMedia.setOnKeyListener(masterItemKeyListener);
        itemManageWhitelist.setOnKeyListener(masterItemKeyListener);

        btnQuickSmartClean.setOnClickListener(v -> performQuickSmartClean());
        itemMemoryMode.setOnClickListener(v -> cycleMemoryMode());
        itemMaxCached.setOnClickListener(v -> cycleMaxCached());
        itemCronInterval.setOnClickListener(v -> cycleCronInterval());
        itemOverloadThreshold.setOnClickListener(v -> cycleOverloadThreshold());
        itemRecentGrace.setOnClickListener(v -> cycleRecentGrace());
        itemProtectMedia.setOnClickListener(v -> toggleProtectMedia());
        itemManageWhitelist.setOnClickListener(v -> openWhitelistSubPanel());

        // Focus hoặc click vào tab là lọc ngay tức thì
        View.OnFocusChangeListener tabFocusListener = (v, hasFocus) -> {
            if (hasFocus) {
                if (v == tabCategoryAll) switchSubPanelFilter("all");
                else if (v == tabCategoryMedia) switchSubPanelFilter("media");
                else if (v == tabCategoryTools) switchSubPanelFilter("tools");
            }
        };
        tabCategoryAll.setOnFocusChangeListener(tabFocusListener);
        tabCategoryMedia.setOnFocusChangeListener(tabFocusListener);
        tabCategoryTools.setOnFocusChangeListener(tabFocusListener);

        tabCategoryAll.setOnClickListener(v -> switchSubPanelFilter("all"));
        tabCategoryMedia.setOnClickListener(v -> switchSubPanelFilter("media"));
        tabCategoryTools.setOnClickListener(v -> switchSubPanelFilter("tools"));

        decor.addView(drawerView);
        isShowing = true;

        if (activity instanceof MainActivity) {
            ((MainActivity) activity).blockMainContentFocus();
        }

        updateRamChartAndStats();
        updateStatuses();

        drawerView.setAlpha(0f);
        drawerView.animate().alpha(1f).setDuration(220).start();

        containerDonutCard.setAlpha(0f);
        containerDonutCard.setTranslationX(-50f);
        containerDonutCard.animate().alpha(1f).translationX(0f).setDuration(260).start();

        drawerContent.setTranslationX(400f);
        drawerContent.animate().translationX(0f).setDuration(220).withEndAction(() -> {
            btnQuickSmartClean.requestFocus();
        }).start();
    }

    public void dismiss() {
        if (!isShowing || drawerView == null) return;

        drawerContent.animate().translationX(400f).setDuration(180).start();
        if (containerDonutCard != null && containerDonutCard.getVisibility() == View.VISIBLE) {
            containerDonutCard.animate().alpha(0f).translationX(-30f).setDuration(180).start();
        }
        drawerView.animate().alpha(0f).setDuration(180).withEndAction(() -> {
            ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
            decor.removeView(drawerView);
            drawerView = null;
            isShowing = false;
            isSubPanelOpen = false;

            if (activity instanceof MainActivity) {
                ((MainActivity) activity).unblockMainContentFocus();
            }

            if (triggerView != null) {
                triggerView.post(() -> {
                    triggerView.requestFocus();
                    if (activity instanceof MainActivity) {
                        ((MainActivity) activity).focusGlassOnCard(triggerView);
                    }
                });
            } else if (activity instanceof MainActivity) {
                ((MainActivity) activity).focusUtilityType(com.next.tv.launcher.utility.UtilitySectionAdapter.TYPE_CLEAN_RAM);
            }
        }).start();
    }

    private void updateRamChartAndStats() {
        updateRamChartAndStats(false);
    }

    private void updateRamChartAndStats(boolean isPostClean) {
        new Thread(() -> {
            int percent = SmartRamGovernor.getRamUsagePercent();
            int totalMb = 1937;
            int usedMb = 1250;

            try {
                ActivityManager am = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                    am.getMemoryInfo(mi);
                    totalMb = (int) (mi.totalMem / (1024 * 1024));
                    usedMb = (int) ((mi.totalMem - mi.availMem) / (1024 * 1024));
                }
            } catch (Exception ignored) {}

            List<LocalAdbBridge.AppProcessMem> processList = adbBridge.parseRunningProcessMem();
            int finalTotalMb = totalMb;
            int finalUsedMb = usedMb;

            activity.runOnUiThread(() -> {
                if (donutChartView != null) {
                    donutChartView.updateData(percent, finalUsedMb, finalTotalMb, config.getMaxCachedProcesses(), processList);
                    List<RamDonutChartView.Segment> segments = donutChartView.getSegments();
                    buildChartLegends(segments);

                    // Log segments info
                    StringBuilder sb = new StringBuilder();
                    for (RamDonutChartView.Segment s : segments) {
                        sb.append("[").append(s.label).append(": ").append(s.pssMb).append("MB] ");
                    }
                    Log.i(TAG, "Donut Segments: " + sb.toString());

                    // Ước tính số MB có thể giải phóng (không ghi đè nếu vừa dọn xong)
                    if (!isPostClean && tvQuickCleanResult != null) {
                        long estimableFreeMb = 0;
                        Set<String> whitelist = config.getWhitelistPackages();
                        for (RamDonutChartView.Segment s : segments) {
                            if (("user".equals(s.category) || "media".equals(s.category)) && !s.packageName.equals(activity.getPackageName()) && !whitelist.contains(s.packageName)) {
                                estimableFreeMb += s.pssMb;
                            }
                        }
                        if (estimableFreeMb > 30) {
                            tvQuickCleanResult.setText("~" + estimableFreeMb + " MB");
                            tvQuickCleanResult.setTextColor(Color.parseColor("#4ADE80"));
                        } else {
                            tvQuickCleanResult.setText("Tối ưu");
                            tvQuickCleanResult.setTextColor(Color.parseColor("#E0E0E0"));
                        }
                    }
                }
            });
        }).start();
    }

    private void buildChartLegends(List<RamDonutChartView.Segment> segments) {
        if (containerLegends == null) return;
        containerLegends.removeAllViews();
        if (segments == null || segments.isEmpty()) return;

        float density = activity.getResources().getDisplayMetrics().density;
        int dotSize = (int) (10f * density);
        int marginDotRight = (int) (6f * density);
        int marginItemRight = (int) (10f * density);

        containerLegends.setOrientation(LinearLayout.HORIZONTAL);
        containerLegends.setGravity(Gravity.CENTER_HORIZONTAL);

        int count = 0;
        for (RamDonutChartView.Segment seg : segments) {
            if (count >= 6) break;

            LinearLayout item = new LinearLayout(activity);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lpItem = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            if (count < segments.size() - 1 && count < 5) {
                lpItem.rightMargin = marginItemRight;
            }
            item.setLayoutParams(lpItem);

            // Chấm tròn 10dp x 10dp bo tròn bằng GradientDrawable OVAL
            View dot = new View(activity);
            LinearLayout.LayoutParams lpDot = new LinearLayout.LayoutParams(dotSize, dotSize);
            lpDot.rightMargin = marginDotRight;
            dot.setLayoutParams(lpDot);

            android.graphics.drawable.GradientDrawable dotDrawable = new android.graphics.drawable.GradientDrawable();
            dotDrawable.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            dotDrawable.setColor(seg.color);
            dot.setBackground(dotDrawable);

            // Nhãn tên ngắn gọn
            TextView tvName = new TextView(activity);
            tvName.setText(seg.label);
            tvName.setTextColor(Color.parseColor("#E2E8F0"));
            tvName.setTextSize(12f);
            tvName.setSingleLine(true);

            item.addView(dot);
            item.addView(tvName);

            containerLegends.addView(item);
            count++;
        }
    }

    private void updateStatuses() {
        String mode = config.getMode();
        boolean isPreset = false;

        if (SmartRamConfigManager.MODE_GENTLE.equals(mode)) {
            tvMemoryModeStatus.setText("Nhẹ nhàng");
            tvMemoryModeStatus.setTextColor(Color.parseColor("#60A5FA"));
            tvModeDescriptionBanner.setText("Giữ 12 app đa nhiệm · Dọn khi quá 88%");
            isPreset = true;
        } else if (SmartRamConfigManager.MODE_STRICT.equals(mode)) {
            tvMemoryModeStatus.setText("Nghiêm ngặt");
            tvMemoryModeStatus.setTextColor(Color.parseColor("#F87171"));
            tvModeDescriptionBanner.setText("Giữ 2 app · Đóng băng nhanh · Dọn khi > 75%");
            isPreset = true;
        } else if (SmartRamConfigManager.MODE_CUSTOM.equals(mode)) {
            tvMemoryModeStatus.setText("Tùy chỉnh");
            tvMemoryModeStatus.setTextColor(Color.parseColor("#FBBF24"));
            tvModeDescriptionBanner.setText("Tùy biến thông số bộ nhớ theo ý bạn");
            isPreset = false;
        } else {
            tvMemoryModeStatus.setText("Tự động");
            tvMemoryModeStatus.setTextColor(Color.parseColor("#4ADE80"));
            tvModeDescriptionBanner.setText("Giữ 6 app hay dùng · Tự dọn thông minh");
            isPreset = true;
        }

        // Làm mờ nhẹ thông số nếu đang ở Preset chuẩn
        float childAlpha = isPreset ? 0.75f : 1.0f;
        itemMaxCached.setAlpha(childAlpha);
        itemCronInterval.setAlpha(childAlpha);
        itemOverloadThreshold.setAlpha(childAlpha);
        itemRecentGrace.setAlpha(childAlpha);

        tvMaxCachedStatus.setText(config.getMaxCachedProcesses() + " ứng dụng");

        int interval = config.getCronIntervalMinutes();
        if (interval == 0) {
            tvCronIntervalStatus.setText("Tắt");
        } else if (interval == 1) {
            tvCronIntervalStatus.setText("Khi về Home");
        } else {
            tvCronIntervalStatus.setText(interval + " phút");
        }

        tvOverloadThresholdStatus.setText(config.getRamOverloadThresholdPercent() + "%");
        tvRecentGraceStatus.setText(config.getRecentGraceMinutes() + " phút");

        boolean protectMedia = config.isProtectActiveMedia();
        tvProtectMediaStatus.setText(protectMedia ? "Bật" : "Tắt");
        tvProtectMediaStatus.setTextColor(protectMedia ? Color.parseColor("#4ADE80") : Color.parseColor("#9E9E9E"));

        int count = config.getWhitelistPackages().size();
        tvWhitelistCountStatus.setText(count + " ứng dụng");
    }

    private void performQuickSmartClean() {
        tvQuickCleanResult.setText("Đang dọn...");
        SmartRamGovernor.cleanRamSmartAsync(activity, true, false, result -> {
            // Trigger 4-Tier Deep Storage Cleaning
            new TvStorageCleaner(activity).startClean(new TvStorageCleaner.CleanProgressListener() {
                @Override
                public void onProgress(String currentPhase, int percent) {
                    activity.runOnUiThread(() -> {
                        if (tvQuickCleanResult != null) {
                            tvQuickCleanResult.setText(percent + "%");
                        }
                    });
                }

                @Override
                public void onComplete(TvStorageCleaner.CleanResult cleanRes) {
                    activity.runOnUiThread(() -> {
                        String summary = "";
                        if (result.freedMb > 0 || result.killedCount > 0) {
                            summary += "-" + result.freedMb + " MB RAM";
                        }
                        if (cleanRes.junkBytesFreed > 0) {
                            if (!summary.isEmpty()) summary += " | ";
                            summary += "+" + TvStorageCleaner.formatSize(cleanRes.junkBytesFreed) + " ROM";
                        }
                        if (summary.isEmpty()) summary = "Đã tối ưu";
                        if (tvQuickCleanResult != null) {
                            tvQuickCleanResult.setText(summary);
                            tvQuickCleanResult.setTextColor(Color.parseColor("#4ADE80"));
                        }
                        Toast.makeText(activity, "Đã dọn dẹp RAM và bộ nhớ TV thành công", Toast.LENGTH_SHORT).show();
                        // Cập nhật lại biểu đồ Donut sau khi dọn xong (các app ngầm vừa bị kill sẽ biến mất)
                        updateRamChartAndStats(true);
                    });
                }
            });
        });
    }

    private void cycleMemoryMode() {
        String mode = config.getMode();
        if (SmartRamConfigManager.MODE_SMART_AUTO.equals(mode)) {
            config.setMode(SmartRamConfigManager.MODE_STRICT);
        } else if (SmartRamConfigManager.MODE_STRICT.equals(mode)) {
            config.setMode(SmartRamConfigManager.MODE_GENTLE);
        } else if (SmartRamConfigManager.MODE_GENTLE.equals(mode)) {
            config.setMode(SmartRamConfigManager.MODE_CUSTOM);
        } else {
            config.setMode(SmartRamConfigManager.MODE_SMART_AUTO);
        }
        SmartRamGovernor.applyKernelLimits(activity);
        updateStatuses();
        updateRamChartAndStats();
    }

    private void cycleMaxCached() {
        int[] options = {2, 4, 6, 8, 12, 16};
        int cur = config.getMaxCachedProcesses();
        int next = options[0];
        for (int i = 0; i < options.length; i++) {
            if (options[i] == cur) {
                next = options[(i + 1) % options.length];
                break;
            }
        }
        config.setMaxCachedProcesses(next);
        config.setMode(SmartRamConfigManager.MODE_CUSTOM);
        SmartRamGovernor.applyKernelLimits(activity);
        updateStatuses();
        updateRamChartAndStats();
    }

    private void cycleCronInterval() {
        int[] options = {0, 1, 5, 15, 30};
        int cur = config.getCronIntervalMinutes();
        int next = options[0];
        for (int i = 0; i < options.length; i++) {
            if (options[i] == cur) {
                next = options[(i + 1) % options.length];
                break;
            }
        }
        config.setCronIntervalMinutes(next);
        config.setMode(SmartRamConfigManager.MODE_CUSTOM);
        updateStatuses();
    }

    private void cycleOverloadThreshold() {
        int[] options = {75, 80, 82, 85, 90};
        int cur = config.getRamOverloadThresholdPercent();
        int next = options[0];
        for (int i = 0; i < options.length; i++) {
            if (options[i] == cur) {
                next = options[(i + 1) % options.length];
                break;
            }
        }
        config.setRamOverloadThresholdPercent(next);
        config.setMode(SmartRamConfigManager.MODE_CUSTOM);
        updateStatuses();
    }

    private void cycleRecentGrace() {
        int[] options = {1, 5, 10, 15};
        int cur = config.getRecentGraceMinutes();
        int next = options[0];
        for (int i = 0; i < options.length; i++) {
            if (options[i] == cur) {
                next = options[(i + 1) % options.length];
                break;
            }
        }
        config.setRecentGraceMinutes(next);
        config.setMode(SmartRamConfigManager.MODE_CUSTOM);
        updateStatuses();
    }

    private void toggleProtectMedia() {
        boolean next = !config.isProtectActiveMedia();
        config.setProtectActiveMedia(next);
        updateStatuses();
    }

    private void openWhitelistSubPanel() {
        if (memorySubPanel == null) return;
        isSubPanelOpen = true;

        // Ẩn mượt mà containerDonutCard để không bị đè chồng
        if (containerDonutCard != null) {
            containerDonutCard.animate()
                    .alpha(0f)
                    .translationX(-40f)
                    .setDuration(150)
                    .withEndAction(() -> containerDonutCard.setVisibility(View.GONE))
                    .start();
        }

        // Tải danh sách app
        PackageManager pm = activity.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        cachedUserApps.clear();
        String myPkg = activity.getPackageName();

        for (ApplicationInfo ai : apps) {
            if (ai.packageName.equals(myPkg)) continue;
            boolean isSystem = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
            if (!isSystem && pm.getLaunchIntentForPackage(ai.packageName) != null) {
                cachedUserApps.add(ai);
            }
        }

        cachedUserApps.sort((a, b) -> a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString()));

        currentSubPanelFilter = "all";
        renderSubPanelItems();

        memorySubPanel.setVisibility(View.VISIBLE);
        float density = activity.getResources().getDisplayMetrics().density;
        int shift = memorySubPanel.getWidth() > 0 ? memorySubPanel.getWidth() : (int) (480 * density);
        memorySubPanel.setTranslationX(shift);
        memorySubPanel.animate().translationX(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .withEndAction(() -> {
            if (containerMemorySubPanelItems.getChildCount() > 0) {
                View firstRow = containerMemorySubPanelItems.getChildAt(0);
                if (firstRow instanceof ViewGroup && ((ViewGroup) firstRow).getChildCount() > 0) {
                    ((ViewGroup) firstRow).getChildAt(0).requestFocus();
                } else if (firstRow != null) {
                    firstRow.requestFocus();
                }
            }
        }).start();
    }

    private void switchSubPanelFilter(String filter) {
        if (filter.equals(currentSubPanelFilter)) return;
        currentSubPanelFilter = filter;

        tabCategoryAll.setTextColor(Color.parseColor("all".equals(filter) ? "#4ADE80" : "#A0A0A0"));
        tabCategoryMedia.setTextColor(Color.parseColor("media".equals(filter) ? "#4ADE80" : "#A0A0A0"));
        tabCategoryTools.setTextColor(Color.parseColor("tools".equals(filter) ? "#4ADE80" : "#A0A0A0"));

        renderSubPanelItems();
    }

    private void renderSubPanelItems() {
        if (containerMemorySubPanelItems == null) return;
        containerMemorySubPanelItems.removeAllViews();

        PackageManager pm = activity.getPackageManager();
        Set<String> whitelist = config.getWhitelistPackages();

        List<ApplicationInfo> filteredList = new ArrayList<>();
        for (ApplicationInfo app : cachedUserApps) {
            String pkg = app.packageName.toLowerCase(Locale.ROOT);
            boolean isMedia = pkg.contains("youtube") || pkg.contains("stremio") || pkg.contains("nuvio")
                    || pkg.contains("netflix") || pkg.contains("spagreen") || pkg.contains("vtv")
                    || pkg.contains("ottplay") || pkg.contains("cloudstream") || pkg.contains("xemtv");

            if ("media".equals(currentSubPanelFilter) && !isMedia) continue;
            if ("tools".equals(currentSubPanelFilter) && isMedia) continue;
            filteredList.add(app);
        }

        float density = activity.getResources().getDisplayMetrics().density;
        int rowGap = (int) (8f * density);
        int colGap = (int) (4f * density);
        int iconSize = (int) (32f * density);
        int lockSize = (int) (26f * density);

        for (int i = 0; i < filteredList.size(); i += 2) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams lpRow = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lpRow.bottomMargin = rowGap;
            row.setLayoutParams(lpRow);
            row.setClipChildren(false);
            row.setClipToPadding(false);

            // Cột 1 (Trái)
            ApplicationInfo app1 = filteredList.get(i);
            View item1 = createGridItem(pm, app1, whitelist, lockSize, iconSize, density, true);
            row.addView(item1);

            // Cột 2 (Phải - nếu có)
            if (i + 1 < filteredList.size()) {
                ApplicationInfo app2 = filteredList.get(i + 1);
                View item2 = createGridItem(pm, app2, whitelist, lockSize, iconSize, density, false);
                row.addView(item2);
            } else {
                View spacer = new View(activity);
                LinearLayout.LayoutParams lpSpacer = new LinearLayout.LayoutParams(0, 1, 1.0f);
                lpSpacer.leftMargin = colGap;
                spacer.setLayoutParams(lpSpacer);
                row.addView(spacer);
            }

            containerMemorySubPanelItems.addView(row);
        }
    }

    private View createGridItem(PackageManager pm, ApplicationInfo app, Set<String> whitelist,
                                int lockSize, int iconSize, float density, boolean isLeftCol) {
        String label = app.loadLabel(pm).toString();
        Drawable icon = app.loadIcon(pm);

        LinearLayout item = new LinearLayout(activity);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding((int) (10f * density), (int) (8f * density), (int) (10f * density), (int) (8f * density));
        item.setBackgroundResource(R.drawable.bg_memory_drawer_item);
        item.setFocusable(true);
        item.setClickable(true);
        setupItemFocusAnim(item);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        if (isLeftCol) {
            lp.rightMargin = (int) (4f * density);
        } else {
            lp.leftMargin = (int) (4f * density);
        }
        item.setLayoutParams(lp);

        // App Icon
        ImageView ivIcon = new ImageView(activity);
        LinearLayout.LayoutParams lpIcon = new LinearLayout.LayoutParams(iconSize, iconSize);
        lpIcon.rightMargin = (int) (8f * density);
        ivIcon.setLayoutParams(lpIcon);
        ivIcon.setImageDrawable(icon);

        // Text Column
        LinearLayout textCol = new LinearLayout(activity);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lpCol = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        textCol.setLayoutParams(lpCol);

        TextView tvLabel = new TextView(activity);
        tvLabel.setText(label);
        tvLabel.setTextColor(Color.WHITE);
        tvLabel.setTextSize(12.5f);
        tvLabel.setSingleLine(true);
        tvLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);

        TextView tvPkg = new TextView(activity);
        tvPkg.setText(app.packageName);
        tvPkg.setTextColor(Color.parseColor("#8E8E93"));
        tvPkg.setTextSize(9.5f);
        tvPkg.setSingleLine(true);
        tvPkg.setEllipsize(android.text.TextUtils.TruncateAt.END);

        textCol.addView(tvLabel);
        textCol.addView(tvPkg);

        // Lock Icon View (Thay thế chữ "Tự dọn" / "Bảo vệ")
        ImageView ivLock = new ImageView(activity);
        LinearLayout.LayoutParams lpLock = new LinearLayout.LayoutParams(lockSize, lockSize);
        lpLock.leftMargin = (int) (4f * density);
        ivLock.setLayoutParams(lpLock);
        ivLock.setPadding((int) (4f * density), (int) (4f * density), (int) (4f * density), (int) (4f * density));
        updateLockIconStyle(ivLock, whitelist.contains(app.packageName));

        item.addView(ivIcon);
        item.addView(textCol);
        item.addView(ivLock);

        item.setOnClickListener(v -> {
            config.toggleWhitelistPackage(app.packageName);
            boolean isNowWhitelisted = config.getWhitelistPackages().contains(app.packageName);
            updateLockIconStyle(ivLock, isNowWhitelisted);
            updateStatuses();
        });

        item.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                    closeSubPanel();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && !isLeftCol) {
                    closeSubPanel();
                    return true;
                }
            }
            return false;
        });

        return item;
    }

    private void updateLockIconStyle(ImageView ivLock, boolean isWhitelisted) {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);

        if (isWhitelisted) {
            ivLock.setImageResource(R.drawable.ic_lock);
            ivLock.setColorFilter(Color.parseColor("#4ADE80"));
            bg.setColor(Color.parseColor("#264ADE80"));
        } else {
            ivLock.setImageResource(R.drawable.ic_unlock);
            ivLock.setColorFilter(Color.parseColor("#71717A"));
            bg.setColor(Color.parseColor("#14FFFFFF"));
        }
        ivLock.setBackground(bg);
    }

    public boolean isSubPanelOpen() {
        return isSubPanelOpen;
    }

    public void closeSubPanel() {
        if (!isSubPanelOpen || memorySubPanel == null) return;
        float density = activity.getResources().getDisplayMetrics().density;
        int shift = memorySubPanel.getWidth() > 0 ? memorySubPanel.getWidth() : (int) (480 * density);
        memorySubPanel.animate().translationX(shift).setDuration(180)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(() -> {
            memorySubPanel.setVisibility(View.GONE);
            isSubPanelOpen = false;

            // Hiện lại containerDonutCard mượt mà
            if (containerDonutCard != null) {
                containerDonutCard.setVisibility(View.VISIBLE);
                containerDonutCard.setAlpha(0f);
                containerDonutCard.setTranslationX(-30f);
                containerDonutCard.animate().alpha(1f).translationX(0f).setDuration(180).start();
            }

            View currentFocus = activity.getCurrentFocus();
            boolean isFocusInMain = currentFocus != null && drawerContent != null && isDescendantOf(drawerContent, currentFocus);
            if (!isFocusInMain && itemManageWhitelist != null) {
                itemManageWhitelist.requestFocus();
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

    private void setupSidebarItemFocus(View view) {
        if (view == null) return;
        view.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.animate().scaleX(1.02f).scaleY(1.02f).setDuration(120).start();
                if (isSubPanelOpen) {
                    closeSubPanel();
                }
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
            }
        });
    }

    private void setupItemFocusAnim(View view) {
        if (view == null) return;
        view.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.animate().scaleX(1.02f).scaleY(1.02f).setDuration(120).start();
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
            }
        });
    }
}
