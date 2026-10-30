package com.next.tv.launcher.utility;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.next.tv.launcher.MainActivity;
import com.next.tv.launcher.R;
import com.next.tv.launcher.SectionConfig;
import com.next.tv.launcher.SystemSettings;
import com.next.tv.launcher.TvRecommendationReader;
import com.next.tv.launcher.lock.AppLockManager;
import com.next.tv.launcher.lock.PinLockDialog;
import com.next.tv.launcher.widget.WidgetConfigManager;
import com.next.tv.launcher.widget.WidgetAddon;
import com.next.tv.launcher.widget.WidgetAddonManager;
import com.next.tv.launcher.widget.AddonConfigServer;
import com.next.tv.launcher.widget.DeviceIpAddress;
import com.next.tv.launcher.widget.QrCodeGenerator;
import android.widget.EditText;
import android.app.AlertDialog;
import android.graphics.Bitmap;

import java.util.List;

public final class AdvancedSettingsDrawer {
    public interface OnAdvancedActionsListener {
        void onManageWidgets();
        void onReorderSections();
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
    private View itemManageWidgets;
    private View itemReorderSections;
    private View itemReducedMotion;
    private View itemDeviceInfo;
    private AddonConfigServer addonConfigServer;
    private Bitmap qrBitmap;
    private ImageView ivQr;
    private int movingWidgetIndex = -1;

    public AdvancedSettingsDrawer(Activity activity, OnAdvancedActionsListener listener) {
        this.activity = activity;
        this.actionsListener = listener;
    }

    public boolean isShowing() {
        return isShowing;
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

        updateStatuses();

        itemManagePin = drawerView.findViewById(R.id.itemManagePin);
        itemManageWidgets = drawerView.findViewById(R.id.itemManageWidgets);
        itemReorderSections = drawerView.findViewById(R.id.itemReorderSections);
        itemReducedMotion = drawerView.findViewById(R.id.itemReducedMotion);
        itemDeviceInfo = drawerView.findViewById(R.id.itemDeviceInfo);

        setupItemFocus(itemManagePin);
        setupItemFocus(itemManageWidgets);
        setupItemFocus(itemReorderSections);
        setupItemFocus(itemReducedMotion);
        setupItemFocus(itemDeviceInfo);
        setupItemFocus(itemUserMode);

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
        if (itemManageWidgets != null) itemManageWidgets.setOnKeyListener(masterItemKeyListener);
        if (itemReorderSections != null) itemReorderSections.setOnKeyListener(masterItemKeyListener);
        if (itemReducedMotion != null) itemReducedMotion.setOnKeyListener(masterItemKeyListener);
        if (itemDeviceInfo != null) itemDeviceInfo.setOnKeyListener(masterItemKeyListener);
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

        if (itemManageWidgets != null) {
            itemManageWidgets.setOnClickListener(v -> { lastOpenedSourceItem = itemManageWidgets; openWidgetsSubPanel(); });
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
        if (drawerContent != null) {
            drawerContent.setTranslationX(400f);
            drawerContent.animate().translationX(0f).setDuration(220).start();
        }

        if (autoFocusMaster) {
            if (itemUserMode != null) {
                itemUserMode.post(() -> itemUserMode.requestFocus());
            } else if (itemManageWidgets != null) {
                itemManageWidgets.post(() -> itemManageWidgets.requestFocus());
            } else if (itemManagePin != null) {
                itemManagePin.post(() -> itemManagePin.requestFocus());
            }
        }
    }

    public void showWidgetsManagement() {
        if (!isShowing) {
            show(null, false);
        }
        lastOpenedSourceItem = itemManageWidgets;
        openWidgetsSubPanel();
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
        ivIcon.setColorFilter(Color.parseColor("#E4E4E7"));
        row.addView(ivIcon);

        LinearLayout textCol = new LinearLayout(activity);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvTitle = new TextView(activity);
        tvTitle.setText(title);
        tvTitle.setTextColor(Color.parseColor("#F4F4F5"));
        tvTitle.setTextSize(14f);
        textCol.addView(tvTitle);

        TextView tvSub = new TextView(activity);
        tvSub.setText(subtitle);
        tvSub.setTextColor(Color.parseColor("#A1A1AA"));
        tvSub.setTextSize(11f);
        textCol.addView(tvSub);

        row.addView(textCol);

        TextView tvStatus = new TextView(activity);
        tvStatus.setText(isSelected ? "Đang chọn" : "");
        tvStatus.setTextColor(Color.parseColor("#22C55E"));
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
            tvTitle.setTextColor(Color.parseColor(hasFocus ? "#09090B" : "#F4F4F5"));
            tvSub.setTextColor(Color.parseColor(hasFocus ? "#52525B" : "#A1A1AA"));
            ivIcon.setColorFilter(Color.parseColor(hasFocus ? "#09090B" : "#E4E4E7"));
            tvStatus.setTextColor(Color.parseColor(hasFocus ? (isSelected ? "#16A34A" : "#52525B") : (isSelected ? "#22C55E" : "#71717A")));
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

    private void stopConfigServer() {
        if (ivQr != null) {
            ivQr.setImageBitmap(null);
            ivQr = null;
        }
        if (qrBitmap != null) {
            if (!qrBitmap.isRecycled()) {
                qrBitmap.recycle();
            }
            qrBitmap = null;
        }
        if (addonConfigServer != null) {
            addonConfigServer.stopSafe();
            addonConfigServer = null;
        }
    }

    private void openWidgetsSubPanel() {
        if (advancedSubPanel == null || containerSubPanelItems == null) return;
        isSubPanelOpen = true;
        stopConfigServer();

        if (tvSubPanelTitle != null) tvSubPanelTitle.setText("QUẢN LÝ TIỆN ÍCH");
        if (tvSubPanelSub != null) tvSubPanelSub.setText("Tùy chỉnh và mở rộng tiện ích");

        containerSubPanelItems.removeAllViews();

        addMenuNavigationRow("Sắp xếp và bật tắt", "Thay đổi vị trí hoặc ẩn hiện", R.drawable.ic_widget_settings, () -> {
            openWidgetReorderScreen();
        });

        addMenuNavigationRow("Cửa hàng tiện ích", "Duyệt kho tiện ích trực tuyến", R.drawable.ic_layers, () -> {
            openWidgetStoreScreen();
        });

        addMenuNavigationRow("Cài đặt từ điện thoại", "Quét mã QR kết nối nhanh", R.drawable.ic_mode_tuning, () -> {
            openWidgetQrScreen();
        });

        addMenuNavigationRow("Thêm từ liên kết", "Cài đặt bằng đường dẫn tệp cấu hình", R.drawable.ic_arrow_right, () -> {
            showAddonUrlDialog();
        });

        animateOpenSubPanel(() -> {
            if (containerSubPanelItems.getChildCount() > 0) {
                View first = containerSubPanelItems.getChildAt(0);
                first.post(() -> first.requestFocus());
            }
        });
    }

    private void notifyWidgetsChanged() {
        if (actionsListener != null) {
            actionsListener.onManageWidgets();
        }
    }

    private void addBackNavigationRow(String title, Runnable onClick) {
        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.drawer_menu_item_bg);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding((int) (14 * density), (int) (10 * density), (int) (14 * density), (int) (10 * density));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (12 * density);
        row.setLayoutParams(lp);

        TextView tvTitle = new TextView(activity);
        tvTitle.setText("< " + title);
        tvTitle.setTextColor(Color.parseColor("#38BDF8"));
        tvTitle.setTextSize(13f);
        tvTitle.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(tvTitle);

        row.setOnClickListener(v -> {
            if (onClick != null) onClick.run();
        });

        row.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate().scaleX(hasFocus ? 1.03f : 1.0f).scaleY(hasFocus ? 1.03f : 1.0f).setDuration(120).start();
            tvTitle.setTextColor(Color.parseColor(hasFocus ? "#09090B" : "#38BDF8"));
        });

        row.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_HOME) {
                    dismiss();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_BACK) {
                    if (onClick != null) onClick.run();
                    return true;
                }
            }
            return false;
        });

        containerSubPanelItems.addView(row);
    }

    private void addMenuNavigationRow(String title, String subtitle, int iconRes, Runnable onClick) {
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
        ivIcon.setImageResource(iconRes);
        int iconSize = (int) (22 * density);
        LinearLayout.LayoutParams lpIcon = new LinearLayout.LayoutParams(iconSize, iconSize);
        lpIcon.setMarginEnd((int) (14 * density));
        ivIcon.setLayoutParams(lpIcon);
        ivIcon.setColorFilter(Color.parseColor("#E4E4E7"));
        row.addView(ivIcon);

        LinearLayout textCol = new LinearLayout(activity);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvTitle = new TextView(activity);
        tvTitle.setText(title);
        tvTitle.setTextColor(Color.parseColor("#F4F4F5"));
        tvTitle.setTextSize(14f);
        textCol.addView(tvTitle);

        TextView tvSub = new TextView(activity);
        tvSub.setText(subtitle);
        tvSub.setTextColor(Color.parseColor("#71717A"));
        tvSub.setTextSize(11f);
        tvSub.setPadding(0, (int) (2 * density), 0, 0);
        textCol.addView(tvSub);

        row.addView(textCol);

        ImageView ivChevron = new ImageView(activity);
        ivChevron.setImageResource(R.drawable.ic_arrow_right);
        int chevSize = (int) (18 * density);
        LinearLayout.LayoutParams lpChev = new LinearLayout.LayoutParams(chevSize, chevSize);
        ivChevron.setLayoutParams(lpChev);
        ivChevron.setColorFilter(Color.parseColor("#71717A"));
        row.addView(ivChevron);

        row.setOnClickListener(v -> {
            if (onClick != null) onClick.run();
        });

        row.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate().scaleX(hasFocus ? 1.03f : 1.0f).scaleY(hasFocus ? 1.03f : 1.0f).setDuration(120).start();
            tvTitle.setTextColor(Color.parseColor(hasFocus ? "#09090B" : "#F4F4F5"));
            tvSub.setTextColor(Color.parseColor(hasFocus ? "#52525B" : "#71717A"));
            ivIcon.setColorFilter(Color.parseColor(hasFocus ? "#09090B" : "#E4E4E7"));
            ivChevron.setColorFilter(Color.parseColor(hasFocus ? "#09090B" : "#71717A"));
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

    private void openWidgetReorderScreen() {
        if (advancedSubPanel == null || containerSubPanelItems == null) return;
        if (tvSubPanelTitle != null) tvSubPanelTitle.setText("SẮP XẾP TIỆN ÍCH");
        if (tvSubPanelSub != null) tvSubPanelSub.setText("Bấm OK để đổi vị trí, giữ để bật tắt");

        refreshWidgetReorderList();
    }

    private void refreshWidgetReorderList() {
        containerSubPanelItems.removeAllViews();
        float density = activity.getResources().getDisplayMetrics().density;

        addBackNavigationRow("Quay lại danh mục", () -> {
            movingWidgetIndex = -1;
            openWidgetsSubPanel();
        });

        WidgetAddonManager addonMgr = WidgetAddonManager.getInstance(activity);
        List<WidgetAddon> list = addonMgr.getInstalledAddons();

        for (int i = 0; i < list.size(); i++) {
            final int index = i;
            final WidgetAddon addon = list.get(i);
            final boolean isBeingMoved = (movingWidgetIndex == index);

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

            LinearLayout textCol = new LinearLayout(activity);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvTitle = new TextView(activity);
            tvTitle.setText(addon.getName());
            tvTitle.setTextColor(Color.parseColor(addon.isEnabled() ? "#F4F4F5" : "#71717A"));
            tvTitle.setTextSize(13f);
            textCol.addView(tvTitle);

            TextView tvBadge = new TextView(activity);
            tvBadge.setText(addon.isBuiltIn() ? "MẶC ĐỊNH" : "MỞ RỘNG");
            tvBadge.setTextSize(10f);
            tvBadge.setTextColor(Color.parseColor(addon.isBuiltIn() ? "#71717A" : "#38BDF8"));
            tvBadge.setPadding(0, (int) (2 * density), 0, 0);
            textCol.addView(tvBadge);

            row.addView(textCol);

            if (isBeingMoved) {
                TextView tvMoving = new TextView(activity);
                tvMoving.setText("Đang đổi (▲/▼)");
                tvMoving.setTextSize(11f);
                tvMoving.setTextColor(Color.parseColor("#38BDF8"));
                tvMoving.setPadding((int) (4 * density), 0, (int) (8 * density), 0);
                row.addView(tvMoving);
            }

            TextView tvStatus = new TextView(activity);
            tvStatus.setText(addon.isEnabled() ? "Bật" : "Tắt");
            tvStatus.setTextColor(Color.parseColor(addon.isEnabled() ? "#22C55E" : "#71717A"));
            tvStatus.setTextSize(12f);
            row.addView(tvStatus);

            row.setOnClickListener(v -> {
                if (movingWidgetIndex == index) {
                    movingWidgetIndex = -1;
                    addonMgr.saveOrderFromList(list);
                    notifyWidgetsChanged();
                    refreshWidgetReorderList();
                } else if (movingWidgetIndex != -1) {
                    movingWidgetIndex = -1;
                    addonMgr.saveOrderFromList(list);
                    notifyWidgetsChanged();
                    refreshWidgetReorderList();
                } else {
                    movingWidgetIndex = index;
                    refreshWidgetReorderList();
                }
            });

            row.setOnLongClickListener(v -> {
                boolean newState = !addon.isEnabled();
                addonMgr.toggleAddon(addon.getId(), newState);
                notifyWidgetsChanged();
                refreshWidgetReorderList();
                Toast.makeText(activity, (newState ? "Đã bật: " : "Đã tắt: ") + addon.getName(), Toast.LENGTH_SHORT).show();
                return true;
            });

            row.setOnFocusChangeListener((v, hasFocus) -> {
                v.animate().scaleX(hasFocus ? 1.03f : 1.0f).scaleY(hasFocus ? 1.03f : 1.0f).setDuration(120).start();
                tvTitle.setTextColor(Color.parseColor(hasFocus ? "#09090B" : (addon.isEnabled() ? "#F4F4F5" : "#71717A")));
            });

            row.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_HOME) {
                        dismiss();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP && movingWidgetIndex == index) {
                        if (index > 0) {
                            addonMgr.moveAddonOrder(index, index - 1);
                            movingWidgetIndex = index - 1;
                            notifyWidgetsChanged();
                            refreshWidgetReorderList();
                        }
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && movingWidgetIndex == index) {
                        if (index < list.size() - 1) {
                            addonMgr.moveAddonOrder(index, index + 1);
                            movingWidgetIndex = index + 1;
                            notifyWidgetsChanged();
                            refreshWidgetReorderList();
                        }
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_BACK) {
                        if (movingWidgetIndex != -1) {
                            movingWidgetIndex = -1;
                            refreshWidgetReorderList();
                            return true;
                        }
                        openWidgetsSubPanel();
                        return true;
                    }
                }
                return false;
            });

            containerSubPanelItems.addView(row);
        }

        if (containerSubPanelItems.getChildCount() > 1) {
            View target = containerSubPanelItems.getChildAt(movingWidgetIndex >= 0 ? movingWidgetIndex + 1 : 1);
            if (target != null) target.post(() -> target.requestFocus());
        }
    }

    private void openWidgetStoreScreen() {
        if (advancedSubPanel == null || containerSubPanelItems == null) return;
        if (tvSubPanelTitle != null) tvSubPanelTitle.setText("CỬA HÀNG TIỆN ÍCH");
        if (tvSubPanelSub != null) tvSubPanelSub.setText("Duyệt và cài đặt tiện ích");

        containerSubPanelItems.removeAllViews();
        float density = activity.getResources().getDisplayMetrics().density;

        addBackNavigationRow("Quay lại danh mục", () -> openWidgetsSubPanel());

        TextView tvLoading = new TextView(activity);
        tvLoading.setText("Đang nạp danh mục trực tuyến...");
        tvLoading.setTextColor(Color.parseColor("#71717A"));
        tvLoading.setTextSize(13f);
        tvLoading.setPadding((int) (14 * density), (int) (16 * density), (int) (14 * density), (int) (16 * density));
        containerSubPanelItems.addView(tvLoading);

        WidgetAddonManager addonMgr = WidgetAddonManager.getInstance(activity);
        addonMgr.fetchStoreCatalog((catalog, success) -> {
            containerSubPanelItems.removeView(tvLoading);
            if (!success || catalog == null || catalog.isEmpty()) {
                catalog = addonMgr.buildDefaultStoreCatalog();
            }

            for (WidgetAddon addon : catalog) {
                final boolean isInst = addonMgr.isInstalled(addon.getId());

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

                LinearLayout textCol = new LinearLayout(activity);
                textCol.setOrientation(LinearLayout.VERTICAL);
                textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView tvTitle = new TextView(activity);
                tvTitle.setText(addon.getName());
                tvTitle.setTextColor(Color.parseColor("#F4F4F5"));
                tvTitle.setTextSize(13f);
                textCol.addView(tvTitle);

                TextView tvDesc = new TextView(activity);
                tvDesc.setText(addon.getDescription() != null && !addon.getDescription().isEmpty() ? addon.getDescription() : addon.getAuthor());
                tvDesc.setTextColor(Color.parseColor("#71717A"));
                tvDesc.setTextSize(11f);
                tvDesc.setPadding(0, (int) (2 * density), 0, 0);
                textCol.addView(tvDesc);

                row.addView(textCol);

                TextView tvAction = new TextView(activity);
                tvAction.setText(isInst ? "Đã cài" : "Cài đặt");
                tvAction.setTextColor(Color.parseColor(isInst ? "#71717A" : "#38BDF8"));
                tvAction.setTextSize(12f);
                tvAction.setPadding((int) (8 * density), 0, 0, 0);
                row.addView(tvAction);

                row.setOnClickListener(v -> {
                    if (isInst) {
                        if (!addon.isBuiltIn()) {
                            addonMgr.uninstallAddon(addon.getId());
                            notifyWidgetsChanged();
                            openWidgetStoreScreen();
                            Toast.makeText(activity, "Đã gỡ tiện ích: " + addon.getName(), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(activity, "Tiện ích hệ thống mặc định", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        addonMgr.installFromStore(addon, (ok, msg, a) -> {
                            notifyWidgetsChanged();
                            openWidgetStoreScreen();
                            Toast.makeText(activity, "Đã cài đặt: " + addon.getName(), Toast.LENGTH_SHORT).show();
                        });
                    }
                });

                row.setOnFocusChangeListener((v, hasFocus) -> {
                    v.animate().scaleX(hasFocus ? 1.03f : 1.0f).scaleY(hasFocus ? 1.03f : 1.0f).setDuration(120).start();
                    tvTitle.setTextColor(Color.parseColor(hasFocus ? "#09090B" : "#F4F4F5"));
                    tvDesc.setTextColor(Color.parseColor(hasFocus ? "#52525B" : "#71717A"));
                    tvAction.setTextColor(Color.parseColor(hasFocus ? "#09090B" : (isInst ? "#71717A" : "#38BDF8")));
                });

                row.setOnKeyListener((v, keyCode, event) -> {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        if (keyCode == KeyEvent.KEYCODE_HOME) {
                            dismiss();
                            return true;
                        }
                        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_BACK) {
                            openWidgetsSubPanel();
                            return true;
                        }
                    }
                    return false;
                });

                containerSubPanelItems.addView(row);
            }
            if (containerSubPanelItems.getChildCount() > 1) {
                View targetRow = containerSubPanelItems.getChildAt(1);
                if (targetRow != null) targetRow.post(() -> targetRow.requestFocus());
            }
        });
    }

    private void openWidgetQrScreen() {
        if (advancedSubPanel == null || containerSubPanelItems == null) return;
        if (tvSubPanelTitle != null) tvSubPanelTitle.setText("CÀI QUA ĐIỆN THOẠI");
        if (tvSubPanelSub != null) tvSubPanelSub.setText("Quét mã để điều khiển nhanh");

        containerSubPanelItems.removeAllViews();
        float density = activity.getResources().getDisplayMetrics().density;

        addBackNavigationRow("Quay lại danh mục", () -> {
            stopConfigServer();
            openWidgetsSubPanel();
        });

        // Start config server
        stopConfigServer();
        addonConfigServer = new AddonConfigServer(activity);
        String sessionToken = String.format(java.util.Locale.US, "%06d", new java.util.Random().nextInt(900000) + 100000);
        addonConfigServer.setSessionToken(sessionToken);
        addonConfigServer.setEventListener(action -> {
            notifyWidgetsChanged();
            Toast.makeText(activity, "Điện thoại đã cập nhật tiện ích", Toast.LENGTH_SHORT).show();
        });
        addonConfigServer.startSafe();

        String ip = DeviceIpAddress.get(activity);
        int port = addonConfigServer.getListeningPort();
        if (port <= 0) port = 8888;
        String serverUrl = "http://" + ip + ":" + port + "/?token=" + sessionToken;

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackgroundResource(R.drawable.drawer_menu_item_bg);
        card.setPadding((int) (18 * density), (int) (18 * density), (int) (18 * density), (int) (18 * density));
        card.setFocusable(true);
        card.setClickable(true);

        LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        card.setLayoutParams(lpCard);

        ivQr = new ImageView(activity);
        int qrSize = (int) (220 * density);
        LinearLayout.LayoutParams lpQr = new LinearLayout.LayoutParams(qrSize, qrSize);
        ivQr.setLayoutParams(lpQr);

        qrBitmap = QrCodeGenerator.generate(serverUrl, qrSize);
        if (qrBitmap != null) {
            ivQr.setImageBitmap(qrBitmap);
        }
        card.addView(ivQr);

        TextView tvUrl = new TextView(activity);
        tvUrl.setText(serverUrl);
        tvUrl.setTextColor(Color.parseColor("#38BDF8"));
        tvUrl.setTextSize(14f);
        tvUrl.setPadding(0, (int) (14 * density), 0, (int) (6 * density));
        card.addView(tvUrl);

        TextView tvHint = new TextView(activity);
        tvHint.setText("Kết nối cùng Wi-Fi và quét mã bằng camera điện thoại để cài đặt hoặc sắp xếp.");
        tvHint.setTextColor(Color.parseColor("#A1A1AA"));
        tvHint.setTextSize(11f);
        tvHint.setGravity(Gravity.CENTER);
        card.addView(tvHint);

        card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_HOME) {
                    dismiss();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_BACK) {
                    stopConfigServer();
                    openWidgetsSubPanel();
                    return true;
                }
            }
            return false;
        });

        containerSubPanelItems.addView(card);
        card.post(() -> card.requestFocus());
    }

    private void showAddonUrlDialog() {
        float density = activity.getResources().getDisplayMetrics().density;
        EditText input = new EditText(activity);
        input.setHint("https://example.com/cau-hinh.json");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.parseColor("#71717A"));
        input.setTextSize(13f);
        input.setPadding((int) (16 * density), (int) (12 * density), (int) (16 * density), (int) (12 * density));

        new AlertDialog.Builder(activity, R.style.Theme_TvHome)
                .setTitle("Thêm tiện ích từ liên kết")
                .setMessage("Nhập liên kết tệp cấu hình tiện ích:")
                .setView(input)
                .setPositiveButton("Cài đặt", (dialog, which) -> {
                    String url = input.getText().toString().trim();
                    if (!url.isEmpty()) {
                        Toast.makeText(activity, "Đang tải cấu hình tiện ích...", Toast.LENGTH_SHORT).show();
                        WidgetAddonManager.getInstance(activity).installFromUrl(url, (success, msg, addon) -> {
                            if (success) {
                                notifyWidgetsChanged();
                                Toast.makeText(activity, "Cài đặt thành công: " + (addon != null ? addon.getName() : ""), Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(activity, "Cài đặt thất bại: " + msg, Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
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
            tvTitle.setTextColor(Color.parseColor(isEnabled ? "#F4F4F5" : "#71717A"));
            tvTitle.setTextSize(13f);
            tvTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(tvTitle);

            if (isBeingMoved) {
                TextView tvMoving = new TextView(activity);
                tvMoving.setText("Đang đổi (▲/▼)");
                tvMoving.setTextSize(11f);
                tvMoving.setTextColor(Color.parseColor("#38BDF8"));
                tvMoving.setPadding((int) (4 * density), 0, (int) (8 * density), 0);
                row.addView(tvMoving);
            }

            TextView tvStatus = new TextView(activity);
            tvStatus.setText(isEnabled ? "Hiện" : "Ẩn");
            tvStatus.setTextColor(Color.parseColor(isEnabled ? "#22C55E" : "#EF4444"));
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
                tvTitle.setTextColor(Color.parseColor(hasFocus ? "#09090B" : (isEnabled ? "#F4F4F5" : "#71717A")));
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
        stopConfigServer();
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
                        || tv.getId() == R.id.tvReducedMotionStatus || tv.getId() == R.id.tvDeviceInfoStatus) {
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
    }

    public void dismiss() {
        if (!isShowing || drawerView == null) return;
        stopConfigServer();

        if (activity instanceof MainActivity) {
            ((MainActivity) activity).unblockMainContentFocus();
            ((MainActivity) activity).focusUtilityItem(0);
        } else if (triggerView != null && triggerView.isShown()) {
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
