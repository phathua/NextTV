package com.next.tv.launcher.utility;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.next.tv.launcher.MainActivity;
import com.next.tv.launcher.R;
import com.next.tv.launcher.rec.SectionConfig;
import com.next.tv.launcher.settings.SystemSettings;
import com.next.tv.launcher.rec.TvRecommendationReader;
import com.next.tv.launcher.lock.AppLockManager;
import com.next.tv.launcher.lock.PinLockDialog;
import com.next.tv.launcher.update.AppUpdateManager;

import java.util.List;

public final class AdvancedSettingsDrawer {
    private static final int COLOR_TEXT_PRIMARY = 0xFFF4F4F5;
    private static final int COLOR_TEXT_MUTED = 0xFF71717A;
    private static final int COLOR_TEXT_SECONDARY = 0xFFA1A1AA;
    private static final int COLOR_TEXT_FOCUSED = 0xFF09090B;
    private static final int COLOR_TEXT_DESC_FOCUSED = 0xFF52525B;
    private static final int COLOR_ACCENT_CYAN = 0xFF38BDF8;
    private static final int COLOR_STATUS_GREEN = 0xFF22C55E;
    private static final int COLOR_STATUS_GREEN_FOCUSED = 0xFF16A34A;
    private static final int COLOR_STATUS_RED = 0xFFEF4444;
    private static final int COLOR_ICON_DEFAULT = 0xFFE4E4E7;

    public interface OnAdvancedActionsListener {
        void onToggleReducedMotion(boolean enabled);
    }

    private final Activity activity;
    private final OnAdvancedActionsListener actionsListener;

    private View drawerView;
    private View drawerContent;
    private View advancedSubPanel;
    private TextView tvSubPanelTitle;
    private TextView tvSubPanelSub;
    private LinearLayout containerSubPanelItems;

    private boolean isShowing = false;
    private boolean isSubPanelOpen = false;
    private View triggerView;

    private TextView tvPinStatus;
    private TextView tvReducedMotionStatus;
    private TextView tvDeviceInfoStatus;
    private View itemUserMode;
    private TextView tvUserModeStatus;
    private View lastOpenedSourceItem;

    private View itemManagePin;
    private View itemReorderSections;
    private View itemReducedMotion;
    private View itemDeviceInfo;
    private View itemAppUpdate;
    private TextView tvAppUpdateStatus;
    private TextView badgeUpdate;
    private final AppUpdateManager.UpdateStateListener updateStateListener;

    public AdvancedSettingsDrawer(Activity activity, OnAdvancedActionsListener listener) {
        this.activity = activity;
        this.actionsListener = listener;
        this.updateStateListener = hasUpdate -> {
            if (this.activity != null) {
                this.activity.runOnUiThread(() -> {
                    if (isShowing) {
                        updateStatuses();
                        if (isSubPanelOpen && lastOpenedSourceItem == itemAppUpdate) {
                            openUpdateSubPanel();
                        }
                    }
                });
            }
        };
    }

    public boolean isShowing() {
        return isShowing;
    }

    public boolean isSubPanelOpen() {
        return isSubPanelOpen;
    }

    public void show() {
        show(null, true);
    }

    public void show(View trigger) {
        show(trigger, true);
    }

    public void show(View trigger, boolean autoFocusMaster) {
        if (isShowing) return;
        this.triggerView = trigger != null ? trigger : activity.getCurrentFocus();

        LayoutInflater inflater = LayoutInflater.from(activity);
        drawerView = inflater.inflate(R.layout.drawer_advanced_settings, null);
        drawerContent = drawerView.findViewById(R.id.advancedSettingsContent);
        advancedSubPanel = drawerView.findViewById(R.id.advancedSubPanel);
        tvSubPanelTitle = drawerView.findViewById(R.id.tvSubPanelTitle);
        tvSubPanelSub = drawerView.findViewById(R.id.tvSubPanelSub);
        containerSubPanelItems = drawerView.findViewById(R.id.containerSubPanelItems);

        tvPinStatus = drawerView.findViewById(R.id.tvPinStatus);
        tvReducedMotionStatus = drawerView.findViewById(R.id.tvReducedMotionStatus);
        tvDeviceInfoStatus = drawerView.findViewById(R.id.tvDeviceInfoStatus);
        itemUserMode = drawerView.findViewById(R.id.itemUserMode);
        tvUserModeStatus = drawerView.findViewById(R.id.tvUserModeStatus);

        itemAppUpdate = drawerView.findViewById(R.id.itemAppUpdate);
        tvAppUpdateStatus = drawerView.findViewById(R.id.tvAppUpdateStatus);
        badgeUpdate = drawerView.findViewById(R.id.badgeUpdate);

        updateStatuses();

        itemManagePin = drawerView.findViewById(R.id.itemManagePin);
        itemReorderSections = drawerView.findViewById(R.id.itemReorderSections);
        itemReducedMotion = drawerView.findViewById(R.id.itemReducedMotion);
        itemDeviceInfo = drawerView.findViewById(R.id.itemDeviceInfo);

        setupItemFocus(itemManagePin);
        setupItemFocus(itemReorderSections);
        setupItemFocus(itemReducedMotion);
        setupItemFocus(itemDeviceInfo);
        setupItemFocus(itemUserMode);
        setupItemFocus(itemAppUpdate);

        View.OnKeyListener masterItemKeyListener = (v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME) {
                    dismiss();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (isSubPanelOpen && containerSubPanelItems != null && containerSubPanelItems.getChildCount() > 0) {
                        View firstSub = containerSubPanelItems.getChildAt(0);
                        if (firstSub != null) firstSub.requestFocus();
                        return true;
                    }
                    dismiss();
                    return true;
                }
            }
            return false;
        };

        if (itemManagePin != null) itemManagePin.setOnKeyListener(masterItemKeyListener);
        if (itemReorderSections != null) itemReorderSections.setOnKeyListener(masterItemKeyListener);
        if (itemReducedMotion != null) itemReducedMotion.setOnKeyListener(masterItemKeyListener);
        if (itemDeviceInfo != null) itemDeviceInfo.setOnKeyListener(masterItemKeyListener);
        if (itemAppUpdate != null) {
            itemAppUpdate.setOnKeyListener(masterItemKeyListener);
            itemAppUpdate.setOnClickListener(v -> { lastOpenedSourceItem = itemAppUpdate; openUpdateSubPanel(); });
        }
        if (itemUserMode != null) {
            itemUserMode.setOnKeyListener(masterItemKeyListener);
            itemUserMode.setOnClickListener(v -> { lastOpenedSourceItem = itemUserMode; openUserModeSubPanel(); });
        }

        if (itemManagePin != null) {
            itemManagePin.setOnClickListener(v -> {
                closeSubPanel();
                PinLockDialog pinDialog = new PinLockDialog(activity, PinLockDialog.MODE_CREATE,
                        "Đặt mã PIN", "Nhập 4 số bảo vệ",
                        new PinLockDialog.PinResultListener() {
                            @Override
                            public void onSuccess() {
                                Toast.makeText(activity, "Đã đặt mã PIN", Toast.LENGTH_SHORT).show();
                                updateStatuses();
                            }

                            @Override
                            public void onCancel() {}
                        });
                pinDialog.show();
            });
        }

        if (itemReorderSections != null) {
            itemReorderSections.setOnClickListener(v -> { lastOpenedSourceItem = itemReorderSections; openReorderSectionsSubPanel(); });
        }

        if (itemReducedMotion != null) {
            itemReducedMotion.setOnClickListener(v -> {
                closeSubPanel();
                boolean cur = false;
                if (activity instanceof MainActivity) {
                    cur = ((MainActivity) activity).isReducedMotion();
                }
                boolean next = !cur;
                if (actionsListener != null) {
                    actionsListener.onToggleReducedMotion(next);
                }
                String msg = next ? "Đã bật giảm chuyển động" : "Đã tắt giảm chuyển động";
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
                updateStatuses();
            });
        }

        if (itemDeviceInfo != null) {
            itemDeviceInfo.setOnClickListener(v -> {
                dismiss();
                openSystemDeviceInfo();
            });
        }

        activity.addContentView(drawerView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (activity instanceof MainActivity) {
            ((MainActivity) activity).blockMainContentFocus();
        }

        isShowing = true;
        isSubPanelOpen = false;
        AppUpdateManager.getInstance(activity).registerListener(updateStateListener);
        if (drawerContent != null) {
            drawerContent.setTranslationX(400f);
            drawerContent.animate().translationX(0f).setDuration(220).start();
        }

        if (autoFocusMaster) {
            if (itemUserMode != null) {
                itemUserMode.post(() -> itemUserMode.requestFocus());
            } else if (itemManagePin != null) {
                itemManagePin.post(() -> itemManagePin.requestFocus());
            }
        }
    }

    private void openUpdateSubPanel() {
        if (advancedSubPanel == null || containerSubPanelItems == null) return;
        isSubPanelOpen = true;

        if (tvSubPanelTitle != null) tvSubPanelTitle.setText("CẬP NHẬT ỨNG DỤNG");
        if (tvSubPanelSub != null) tvSubPanelSub.setText("Thông tin phiên bản & thay đổi");
        containerSubPanelItems.removeAllViews();

        float density = activity.getResources().getDisplayMetrics().density;
        AppUpdateManager updateMgr = AppUpdateManager.getInstance(activity);
        boolean hasUpdate = updateMgr.isUpdateAvailable();
        boolean isSideload = updateMgr.isSideload();
        String currentVer = updateMgr.getCurrentVersion();
        String latestVer = updateMgr.getLatestVersion();

        // 1. Khung trạng thái phiên bản
        LinearLayout statusCard = new LinearLayout(activity);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setBackgroundResource(R.drawable.drawer_menu_item_bg);
        statusCard.setPadding((int) (16 * density), (int) (14 * density), (int) (16 * density), (int) (14 * density));
        LinearLayout.LayoutParams lpStatus = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpStatus.bottomMargin = (int) (12 * density);
        statusCard.setLayoutParams(lpStatus);

        TextView tvCardVer = new TextView(activity);
        tvCardVer.setText(hasUpdate ? ("Bản mới: v" + latestVer) : ("Phiên bản: v" + currentVer));
        tvCardVer.setTextColor(COLOR_TEXT_PRIMARY);
        tvCardVer.setTextSize(15f);
        statusCard.addView(tvCardVer);

        TextView tvCardSub = new TextView(activity);
        if (hasUpdate) {
            tvCardSub.setText("Bản hiện tại: v" + currentVer + " • Đã có bản mới sẵn sàng");
            tvCardSub.setTextColor(COLOR_ACCENT_CYAN);
        } else {
            tvCardSub.setText("Bạn đang sử dụng phiên bản mới nhất của NextTV");
            tvCardSub.setTextColor(COLOR_STATUS_GREEN);
        }
        tvCardSub.setTextSize(11f);
        tvCardSub.setPadding(0, (int) (4 * density), 0, 0);
        statusCard.addView(tvCardSub);

        containerSubPanelItems.addView(statusCard);

        // 2. Nút hành động Cập nhật / Kiểm tra
        LinearLayout actionBtn = new LinearLayout(activity);
        actionBtn.setId(R.id.btnActionUpdate);
        actionBtn.setOrientation(LinearLayout.VERTICAL);
        actionBtn.setGravity(Gravity.CENTER);
        actionBtn.setBackgroundResource(R.drawable.drawer_menu_item_bg);
        actionBtn.setFocusable(true);
        actionBtn.setClickable(true);
        actionBtn.setPadding((int) (16 * density), (int) (14 * density), (int) (16 * density), (int) (14 * density));
        LinearLayout.LayoutParams lpAction = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpAction.bottomMargin = (int) (14 * density);
        actionBtn.setLayoutParams(lpAction);

        TextView tvActionTitle = new TextView(activity);
        tvActionTitle.setTextSize(14f);
        tvActionTitle.setTextColor(COLOR_TEXT_PRIMARY);

        TextView tvActionDesc = new TextView(activity);
        tvActionDesc.setTextSize(11f);
        tvActionDesc.setTextColor(COLOR_TEXT_SECONDARY);
        tvActionDesc.setPadding(0, (int) (2 * density), 0, 0);

        // Thanh tiến trình tải tệp (cho bản Sideload khi đang tải)
        ProgressBar pbDownload = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        pbDownload.setId(R.id.subPanelUpdateProgressBar);
        pbDownload.setMax(100);
        pbDownload.setVisibility(View.GONE);
        LinearLayout.LayoutParams lpPb = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (6 * density));
        lpPb.topMargin = (int) (8 * density);
        pbDownload.setLayoutParams(lpPb);
        pbDownload.setProgressDrawable(activity.getDrawable(R.drawable.ram_progress_bar_bg));

        if (isSideload) {
            if (hasUpdate) {
                long apkSize = updateMgr.getApkSize();
                String sizeStr = apkSize > 0 ? String.format(java.util.Locale.getDefault(), " (%.1f MB)", apkSize / (1024f * 1024f)) : "";
                tvActionTitle.setText("Tải và cài đặt cập nhật");
                tvActionDesc.setText("Nhấn để tải gói APK cập nhật mới" + sizeStr);
            } else {
                tvActionTitle.setText("Kiểm tra bản mới");
                tvActionDesc.setText("Quét kho cập nhật GitHub phathua/NextTV");
            }
        } else {
            // Google Play build
            if (hasUpdate) {
                tvActionTitle.setText("Cập nhật qua CH Play");
                tvActionDesc.setText("Mở Google Play Store để cài đặt phiên bản mới");
            } else {
                tvActionTitle.setText("Xem trên CH Play");
                tvActionDesc.setText("Mở trang NextTV trên Google Play Store");
            }
        }

        actionBtn.addView(tvActionTitle);
        actionBtn.addView(tvActionDesc);
        actionBtn.addView(pbDownload);

        actionBtn.setOnClickListener(v -> {
            if (isSideload) {
                if (hasUpdate) {
                    if (updateMgr.isDownloading()) return;
                    tvActionTitle.setText("Đang chuẩn bị tải...");
                    tvActionDesc.setText("Vui lòng giữ TV kết nối mạng");
                    pbDownload.setVisibility(View.VISIBLE);
                    pbDownload.setProgress(0);

                    updateMgr.downloadAndInstall(activity, new AppUpdateManager.DownloadProgressListener() {
                        @Override
                        public void onProgress(long downloadedBytes, long totalBytes, int percent) {
                            if (percent >= 0) {
                                pbDownload.setProgress(percent);
                                float mbDown = downloadedBytes / (1024f * 1024f);
                                float mbTotal = totalBytes / (1024f * 1024f);
                                tvActionTitle.setText("Đang tải bản cập nhật: " + percent + "%");
                                tvActionDesc.setText(String.format(java.util.Locale.getDefault(), "%.1f / %.1f MB", mbDown, mbTotal));
                            }
                        }

                        @Override
                        public void onSuccess(java.io.File downloadedApk) {
                            pbDownload.setVisibility(View.GONE);
                            tvActionTitle.setText("Đã tải xong!");
                            tvActionDesc.setText("Đang kích hoạt trình cài đặt...");
                        }

                        @Override
                        public void onError(String errorMessage) {
                            pbDownload.setVisibility(View.GONE);
                            tvActionTitle.setText("Tải và cài đặt cập nhật");
                            tvActionDesc.setText("Lỗi: " + errorMessage + " (Nhấn để thử lại)");
                            Toast.makeText(activity, errorMessage, Toast.LENGTH_LONG).show();
                        }
                    });
                } else {
                    tvActionTitle.setText("Đang kiểm tra...");
                    tvActionDesc.setText("Kết nối kho phathua/NextTV...");
                    updateMgr.checkForUpdates(true, new AppUpdateManager.UpdateCheckCallback() {
                        @Override
                        public void onCheckCompleted(boolean hasUp, String latestV, List<String> changelog) {
                            updateStatuses();
                            openUpdateSubPanel();
                            String msg = hasUp ? ("Đã tìm thấy bản mới: v" + latestV) : "Đang sử dụng bản mới nhất";
                            Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onCheckFailed(String error) {
                            openUpdateSubPanel();
                            Toast.makeText(activity, error, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } else {
                updateMgr.openPlayStore(activity);
            }
        });

        actionBtn.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate().scaleX(hasFocus ? 1.03f : 1.0f).scaleY(hasFocus ? 1.03f : 1.0f).setDuration(120).start();
            tvActionTitle.setTextColor(hasFocus ? COLOR_TEXT_FOCUSED : COLOR_TEXT_PRIMARY);
            tvActionDesc.setTextColor(hasFocus ? COLOR_TEXT_DESC_FOCUSED : COLOR_TEXT_SECONDARY);
        });

        actionBtn.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_HOME) {
                    dismiss();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_BACK) {
                    closeSubPanel();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    return true;
                }
            }
            return false;
        });

        containerSubPanelItems.addView(actionBtn);

        // 3. Tiêu đề mục Thay đổi
        TextView tvChangelogHeader = new TextView(activity);
        tvChangelogHeader.setText("CÁC THAY ĐỔI MỚI");
        tvChangelogHeader.setTextSize(12f);
        tvChangelogHeader.setTextColor(COLOR_TEXT_SECONDARY);
        tvChangelogHeader.setLetterSpacing(0.06f);
        tvChangelogHeader.setPadding(0, 0, 0, (int) (8 * density));
        containerSubPanelItems.addView(tvChangelogHeader);

        // 4. Danh sách các commit / thay đổi (không hiển thị mã số commit)
        List<String> changelog = updateMgr.getCleanChangelog();
        if (changelog != null && !changelog.isEmpty()) {
            for (String change : changelog) {
                LinearLayout itemRow = new LinearLayout(activity);
                itemRow.setOrientation(LinearLayout.HORIZONTAL);
                itemRow.setGravity(Gravity.TOP);
                itemRow.setPadding(0, (int) (4 * density), 0, (int) (6 * density));

                TextView bullet = new TextView(activity);
                bullet.setText("•");
                bullet.setTextSize(14f);
                bullet.setTextColor(COLOR_ACCENT_CYAN);
                bullet.setPadding(0, 0, (int) (8 * density), 0);
                itemRow.addView(bullet);

                TextView tvChange = new TextView(activity);
                tvChange.setText(change);
                tvChange.setTextSize(12f);
                tvChange.setTextColor(COLOR_TEXT_PRIMARY);
                tvChange.setLayoutParams(new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                itemRow.addView(tvChange);

                containerSubPanelItems.addView(itemRow);
            }
        } else {
            TextView tvEmpty = new TextView(activity);
            tvEmpty.setText("Chưa có ghi chú chi tiết cho bản này");
            tvEmpty.setTextSize(12f);
            tvEmpty.setTextColor(COLOR_TEXT_MUTED);
            tvEmpty.setPadding(0, (int) (4 * density), 0, (int) (4 * density));
            containerSubPanelItems.addView(tvEmpty);
        }

        animateOpenSubPanel(() -> {
            actionBtn.post(() -> actionBtn.requestFocus());
        });
    }

    private void openUserModeSubPanel() {
        isSubPanelOpen = true;
        tvSubPanelTitle.setText("CHẾ ĐỘ HOẠT ĐỘNG");
        tvSubPanelSub.setText("Tối ưu CPU & RAM cho TV của bạn");
        containerSubPanelItems.removeAllViews();

        int currentMode = activity.getSharedPreferences("tvhome_prefs", Context.MODE_PRIVATE).getInt("user_mode", 0);

        addUserModeRow(0, "Đầy đủ", "Trải nghiệm phong phú, toàn diện", currentMode == 0);
        addUserModeRow(1, "Cân bằng", "Mượt mà 60fps, tối ưu RAM", currentMode == 1);
        addUserModeRow(2, "Siêu nhẹ", "Chỉ hiện app, tối đa hiệu năng", currentMode == 2);

        animateOpenSubPanel(() -> {
            if (containerSubPanelItems.getChildCount() > 0) {
                View target = containerSubPanelItems.getChildAt(currentMode);
                if (target == null) target = containerSubPanelItems.getChildAt(0);
                final View fTarget = target;
                fTarget.post(() -> fTarget.requestFocus());
            }
        });
    }

    private void addUserModeRow(int mode, String title, String subtitle, boolean isSelected) {
        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.drawer_menu_item_bg);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding((int) (14 * density), (int) (12 * density), (int) (14 * density), (int) (12 * density));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (8 * density);
        row.setLayoutParams(lp);

        ImageView ivIcon = new ImageView(activity);
        int iconRes = (mode == 0) ? R.drawable.ic_layers : (mode == 1 ? R.drawable.ic_mode_gauge : R.drawable.ic_leaf);
        ivIcon.setImageResource(iconRes);
        int iconSize = (int) (22 * density);
        LinearLayout.LayoutParams lpIcon = new LinearLayout.LayoutParams(iconSize, iconSize);
        lpIcon.rightMargin = (int) (12 * density);
        ivIcon.setLayoutParams(lpIcon);
        ivIcon.setColorFilter(COLOR_ICON_DEFAULT);
        row.addView(ivIcon);

        LinearLayout textCol = new LinearLayout(activity);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvTitle = new TextView(activity);
        tvTitle.setText(title);
        tvTitle.setTextColor(COLOR_TEXT_PRIMARY);
        tvTitle.setTextSize(14f);
        textCol.addView(tvTitle);

        TextView tvSub = new TextView(activity);
        tvSub.setText(subtitle);
        tvSub.setTextColor(COLOR_TEXT_SECONDARY);
        tvSub.setTextSize(11f);
        textCol.addView(tvSub);

        row.addView(textCol);

        TextView tvStatus = new TextView(activity);
        tvStatus.setText(isSelected ? "Đang chọn" : "");
        tvStatus.setTextColor(COLOR_STATUS_GREEN);
        tvStatus.setTextSize(12f);
        row.addView(tvStatus);

        row.setOnClickListener(v -> {
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).applyUserMode(mode);
            }
            updateStatuses();
            openUserModeSubPanel();
            Toast.makeText(activity, "Đã chuyển: " + title, Toast.LENGTH_SHORT).show();
        });

        row.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate().scaleX(hasFocus ? 1.03f : 1.0f).scaleY(hasFocus ? 1.03f : 1.0f).setDuration(120).start();
            tvTitle.setTextColor(hasFocus ? COLOR_TEXT_FOCUSED : COLOR_TEXT_PRIMARY);
            tvSub.setTextColor(hasFocus ? COLOR_TEXT_DESC_FOCUSED : COLOR_TEXT_SECONDARY);
            ivIcon.setColorFilter(hasFocus ? COLOR_TEXT_FOCUSED : COLOR_ICON_DEFAULT);
            tvStatus.setTextColor(hasFocus ? (isSelected ? COLOR_STATUS_GREEN_FOCUSED : COLOR_TEXT_DESC_FOCUSED) : (isSelected ? COLOR_STATUS_GREEN : COLOR_TEXT_MUTED));
        });

        row.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_HOME) {
                    dismiss();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_BACK) {
                    closeSubPanel();
                    return true;
                }
            }
            return false;
        });

        containerSubPanelItems.addView(row);
    }

    private void openReorderSectionsSubPanel() {
        if (advancedSubPanel == null || containerSubPanelItems == null) return;
        isSubPanelOpen = true;

        if (tvSubPanelTitle != null) tvSubPanelTitle.setText("SẮP XẾP MÀN HÌNH");
        if (tvSubPanelSub != null) tvSubPanelSub.setText("Thay đổi vị trí và ẩn hiện hàng");

        refreshReorderSectionsList();

        animateOpenSubPanel(() -> {
            if (containerSubPanelItems.getChildCount() > 0) {
                View first = containerSubPanelItems.getChildAt(0);
                first.post(() -> first.requestFocus());
            }
        });
    }

    private int movingSectionIndex = -1;

    private void refreshReorderSectionsList() {
        containerSubPanelItems.removeAllViews();
        SectionConfig sectionConfig = new SectionConfig(activity);
        List<String> order = sectionConfig.getSectionOrder();

        // Discover and append any dynamic movie sections (like VTVGo / Recs) currently loaded
        if (activity instanceof MainActivity) {
            List<TvRecommendationReader.MovieSection> loaded = ((MainActivity) activity).getMovieSections();
            boolean addedNew = false;
            if (loaded != null) {
                for (TvRecommendationReader.MovieSection sec : loaded) {
                    if (sec != null && sec.id != null && !order.contains(sec.id)) {
                        order.add(sec.id);
                        addedNew = true;
                    }
                }
            }
            if (addedNew) {
                sectionConfig.saveSectionOrder(order);
            }
        }

        float density = activity.getResources().getDisplayMetrics().density;

        for (int i = 0; i < order.size(); i++) {
            final int index = i;
            final String secId = order.get(i);
            String title = getSectionDisplayName(secId);
            boolean isEnabled = sectionConfig.isSectionEnabled(secId);
            final boolean isBeingMoved = (movingSectionIndex == index);

            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.drawer_menu_item_bg);
            row.setFocusable(true);
            row.setClickable(true);
            row.setPadding((int) (14 * density), (int) (10 * density), (int) (14 * density), (int) (10 * density));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int) (8 * density);
            row.setLayoutParams(lp);

            TextView tvTitle = new TextView(activity);
            tvTitle.setText(title);
            tvTitle.setTextColor(isEnabled ? COLOR_TEXT_PRIMARY : COLOR_TEXT_MUTED);
            tvTitle.setTextSize(13f);
            tvTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(tvTitle);

            if (isBeingMoved) {
                TextView tvMoving = new TextView(activity);
                tvMoving.setText("Đang đổi (▲/▼)");
                tvMoving.setTextSize(11f);
                tvMoving.setTextColor(COLOR_ACCENT_CYAN);
                tvMoving.setPadding((int) (4 * density), 0, (int) (8 * density), 0);
                row.addView(tvMoving);
            }

            TextView tvStatus = new TextView(activity);
            tvStatus.setText(isEnabled ? "Hiện" : "Ẩn");
            tvStatus.setTextColor(isEnabled ? COLOR_STATUS_GREEN : COLOR_STATUS_RED);
            tvStatus.setTextSize(12f);
            row.addView(tvStatus);

            row.setOnClickListener(v -> {
                if (movingSectionIndex == index) {
                    // Enter / OK confirms and drops section
                    movingSectionIndex = -1;
                    sectionConfig.saveSectionOrder(order);
                    if (activity instanceof MainActivity) {
                        ((MainActivity) activity).applySectionOrderFromConfig();
                    }
                    refreshReorderSectionsList();
                    containerSubPanelItems.post(() -> {
                        if (index < containerSubPanelItems.getChildCount()) {
                            containerSubPanelItems.getChildAt(index).requestFocus();
                        }
                    });
                } else if (movingSectionIndex != -1) {
                    // Clicking another row finishes reordering
                    movingSectionIndex = -1;
                    sectionConfig.saveSectionOrder(order);
                    if (activity instanceof MainActivity) {
                        ((MainActivity) activity).applySectionOrderFromConfig();
                    }
                    refreshReorderSectionsList();
                } else {
                    // Click selects row to start reordering (like section manager)
                    movingSectionIndex = index;
                    refreshReorderSectionsList();
                    containerSubPanelItems.post(() -> {
                        if (index < containerSubPanelItems.getChildCount()) {
                            containerSubPanelItems.getChildAt(index).requestFocus();
                        }
                    });
                }
            });

            row.setOnLongClickListener(v -> {
                // Long press toggles visibility (except Apps section)
                if (!SectionConfig.SECTION_APPS.equals(secId)) {
                    boolean nextState = !sectionConfig.isSectionEnabled(secId);
                    sectionConfig.setSectionEnabled(secId, nextState);
                    if (activity instanceof MainActivity) {
                        ((MainActivity) activity).applySectionOrderFromConfig();
                    }
                    refreshReorderSectionsList();
                    containerSubPanelItems.post(() -> {
                        if (index < containerSubPanelItems.getChildCount()) {
                            containerSubPanelItems.getChildAt(index).requestFocus();
                        }
                    });
                } else {
                    Toast.makeText(activity, "Hàng ứng dụng luôn hiện", Toast.LENGTH_SHORT).show();
                }
                return true;
            });

            row.setOnFocusChangeListener((v, hasFocus) -> {
                v.animate().scaleX(hasFocus ? 1.03f : 1.0f).scaleY(hasFocus ? 1.03f : 1.0f).setDuration(120).start();
                tvTitle.setTextColor(hasFocus ? COLOR_TEXT_FOCUSED : (isEnabled ? COLOR_TEXT_PRIMARY : COLOR_TEXT_MUTED));
            });

            row.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_HOME) {
                        movingSectionIndex = -1;
                        dismiss();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        // Trap boundary on left - do not leak focus to background
                        return true;
                    }
                    if (movingSectionIndex == index) {
                        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                            if (index > 0) {
                                String item = order.remove(index);
                                order.add(index - 1, item);
                                sectionConfig.saveSectionOrder(order);
                                if (activity instanceof MainActivity) {
                                    ((MainActivity) activity).applySectionOrderFromConfig();
                                }
                                movingSectionIndex = index - 1;
                                refreshReorderSectionsList();
                                containerSubPanelItems.post(() -> {
                                    if (movingSectionIndex < containerSubPanelItems.getChildCount()) {
                                        containerSubPanelItems.getChildAt(movingSectionIndex).requestFocus();
                                    }
                                });
                            }
                            return true;
                        }
                        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                            if (index < order.size() - 1) {
                                String item = order.remove(index);
                                order.add(index + 1, item);
                                sectionConfig.saveSectionOrder(order);
                                if (activity instanceof MainActivity) {
                                    ((MainActivity) activity).applySectionOrderFromConfig();
                                }
                                movingSectionIndex = index + 1;
                                refreshReorderSectionsList();
                                containerSubPanelItems.post(() -> {
                                    if (movingSectionIndex < containerSubPanelItems.getChildCount()) {
                                        containerSubPanelItems.getChildAt(movingSectionIndex).requestFocus();
                                    }
                                });
                            }
                            return true;
                        }
                        if (keyCode == KeyEvent.KEYCODE_BACK) {
                            // Cancel moving mode on Back
                            movingSectionIndex = -1;
                            refreshReorderSectionsList();
                            containerSubPanelItems.post(() -> {
                                if (index < containerSubPanelItems.getChildCount()) {
                                    containerSubPanelItems.getChildAt(index).requestFocus();
                                }
                            });
                            return true;
                        }
                    } else {
                        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_BACK) {
                            movingSectionIndex = -1;
                            closeSubPanel();
                            return true;
                        }
                    }
                }
                return false;
            });

            containerSubPanelItems.addView(row);
        }
    }

    private String getSectionDisplayName(String secId) {
        if (SectionConfig.SECTION_APPS.equals(secId)) return "Ứng dụng TV";
        if (SectionConfig.SECTION_CONTINUE_WATCHING.equals(secId)) return "Tiếp tục xem";
        if (SectionConfig.SECTION_LIVE_TV.equals(secId)) return "Kênh truyền hình";
        if (secId != null && activity instanceof MainActivity) {
            List<TvRecommendationReader.MovieSection> loaded = ((MainActivity) activity).getMovieSections();
            if (loaded != null) {
                for (TvRecommendationReader.MovieSection sec : loaded) {
                    if (sec != null && secId.equals(sec.id) && sec.title != null && !sec.title.isEmpty()) {
                        return sec.title;
                    }
                }
            }
        }
        return secId;
    }

    private void animateOpenSubPanel(Runnable postAction) {
        if (advancedSubPanel == null) return;
        isSubPanelOpen = true;
        advancedSubPanel.setVisibility(View.VISIBLE);
        float density = activity.getResources().getDisplayMetrics().density;
        int shift = advancedSubPanel.getWidth() > 0 ? advancedSubPanel.getWidth() : (int) (380 * density);
        advancedSubPanel.setTranslationX(shift);
        advancedSubPanel.animate().translationX(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .withEndAction(() -> {
                    if (postAction != null) postAction.run();
                }).start();
    }

    public void closeSubPanel() {
        if (!isSubPanelOpen || advancedSubPanel == null) return;
        isSubPanelOpen = false;
        float density = activity.getResources().getDisplayMetrics().density;
        int shift = advancedSubPanel.getWidth() > 0 ? advancedSubPanel.getWidth() : (int) (380 * density);
        advancedSubPanel.animate().translationX(shift).setDuration(180)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(() -> {
                    advancedSubPanel.setVisibility(View.GONE);
                    View currentFocus = activity.getCurrentFocus();
                    boolean isFocusInMain = currentFocus != null && drawerContent != null && isDescendantOf(drawerContent, currentFocus);
                    if (!isFocusInMain) {
                        if (lastOpenedSourceItem != null) {
                            lastOpenedSourceItem.requestFocus();
                        } else if (itemUserMode != null) {
                            itemUserMode.requestFocus();
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

    private void openSystemDeviceInfo() {
        try {
            // First try standard Device Info settings for Android TV
            Intent intent = SystemSettings.intent(activity, Settings.ACTION_DEVICE_INFO_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e1) {
            try {
                // Fallback to general system settings
                Intent intent = SystemSettings.intent(activity, Settings.ACTION_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
            } catch (Exception e2) {
                Toast.makeText(activity, "Không thể mở Cài đặt", Toast.LENGTH_SHORT).show();
            }
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
                if (tv.getId() == R.id.tvPinStatus
                        || tv.getId() == R.id.tvReducedMotionStatus
                        || tv.getId() == R.id.tvDeviceInfoStatus
                        || tv.getId() == R.id.tvAppUpdateStatus) {
                    tv.setTextColor(hasFocus ? COLOR_TEXT_DESC_FOCUSED : COLOR_TEXT_SECONDARY);
                } else if (tv.getId() == R.id.badgeUpdate) {
                    // Badge luôn giữ màu chữ trắng nổi bật
                    tv.setTextColor(0xFFFFFFFF);
                } else {
                    tv.setTextColor(hasFocus ? COLOR_TEXT_FOCUSED : COLOR_TEXT_PRIMARY);
                }
            } else if (child instanceof ImageView) {
                ((ImageView) child).setColorFilter(hasFocus ? COLOR_TEXT_FOCUSED : COLOR_ICON_DEFAULT);
            } else if (child instanceof ViewGroup) {
                updateChildColors((ViewGroup) child, hasFocus);
            }
        }
    }

    private void updateStatuses() {
        if (tvUserModeStatus != null) {
            int mode = activity.getSharedPreferences("tvhome_prefs", Context.MODE_PRIVATE).getInt("user_mode", 0);
            tvUserModeStatus.setText(mode == 2 ? "Siêu nhẹ" : mode == 1 ? "Cân bằng" : "Đầy đủ");
        }
        AppLockManager lockManager = AppLockManager.getInstance(activity);
        if (tvPinStatus != null) {
            boolean hasPin = lockManager.hasPin();
            tvPinStatus.setText(hasPin ? "Đã bật bảo vệ 4 số" : "Bảo vệ cài đặt 4 số");
        }
        if (tvReducedMotionStatus != null) {
            boolean isReduced = false;
            if (activity instanceof MainActivity) {
                isReduced = ((MainActivity) activity).isReducedMotion();
            }
            tvReducedMotionStatus.setText(isReduced ? "Đang bật (Tiết kiệm)" : "Đang tắt (Mượt mà)");
        }
        if (tvDeviceInfoStatus != null) {
            tvDeviceInfoStatus.setText("Cài đặt hệ thống TV");
        }
        AppUpdateManager updateMgr = AppUpdateManager.getInstance(activity);
        boolean hasUpdate = updateMgr.isUpdateAvailable();
        if (badgeUpdate != null) {
            badgeUpdate.setVisibility(hasUpdate ? View.VISIBLE : View.GONE);
        }
        if (tvAppUpdateStatus != null) {
            if (hasUpdate) {
                tvAppUpdateStatus.setText("Có bản mới " + updateMgr.getLatestVersion());
                tvAppUpdateStatus.setTextColor(COLOR_ACCENT_CYAN);
            } else {
                tvAppUpdateStatus.setText("Phiên bản " + updateMgr.getCurrentVersion() + " (Mới nhất)");
                tvAppUpdateStatus.setTextColor(COLOR_TEXT_SECONDARY);
            }
        }
    }

    public void dismiss() {
        if (!isShowing || drawerView == null) return;

        AppUpdateManager.getInstance(activity).unregisterListener(updateStateListener);

        if (activity instanceof MainActivity) {
            ((MainActivity) activity).unblockMainContentFocus();
        }
        if (triggerView != null && triggerView.isShown()) {
            triggerView.post(() -> {
                triggerView.requestFocus();
                if (activity instanceof MainActivity) {
                    ((MainActivity) activity).focusGlassOnCard(triggerView);
                }
            });
        } else if (activity instanceof MainActivity) {
            ((MainActivity) activity).focusUtilityType(UtilitySectionAdapter.TYPE_SETTINGS);
        }

        if (drawerContent != null) {
            drawerContent.animate().translationX(400f).setDuration(180).withEndAction(() -> {
                try {
                    ViewGroup parent = (ViewGroup) drawerView.getParent();
                    if (parent != null) parent.removeView(drawerView);
                } catch (Exception ignored) {}
                drawerView = null;
                isShowing = false;
                isSubPanelOpen = false;
            }).start();
        } else {
            try {
                ViewGroup parent = (ViewGroup) drawerView.getParent();
                if (parent != null) parent.removeView(drawerView);
            } catch (Exception ignored) {}
            drawerView = null;
            isShowing = false;
            isSubPanelOpen = false;
        }
    }
}
