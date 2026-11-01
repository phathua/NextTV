package com.next.tv.launcher.widget;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.next.tv.launcher.MainActivity;
import com.next.tv.launcher.R;
import com.next.tv.launcher.util.FastPosterLoader;
import com.next.tv.launcher.utility.UtilitySectionAdapter;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Ngăn kéo Quản lý và Cửa hàng tiện ích theo phong cách 10-foot TV cao cấp.
 * Đồng bộ với AdvancedSettingsDrawer: nền tối OLED chống ánh sáng xanh, thẻ bo góc 12dp,
 * nút bấm kích thước lớn dễ nhìn từ 3 mét, điều hướng D-pad trực giao mượt mà.
 */
public final class WidgetManagerDrawer {
    private static final int MAIN = 0;
    private static final int REORDER = 1;
    private static final int STORE = 2;
    private static final int QR = 3;
    private static final int URL = 4;
    private static final int CONFIRM = 5;

    private static final int COLOR_TEXT_PRIMARY = 0xFFF4F4F5;
    private static final int COLOR_TEXT_MUTED = 0xFF71717A;
    private static final int COLOR_TEXT_SECONDARY = 0xFFA1A1AA;
    private static final int COLOR_TEXT_FOCUSED = 0xFF09090B;
    private static final int COLOR_TEXT_DESC_FOCUSED = 0xFF52525B;
    private static final int COLOR_ACCENT_CYAN = 0xFF38BDF8;
    private static final int COLOR_STATUS_GREEN = 0xFF22C55E;
    private static final int COLOR_STATUS_GREEN_FOCUSED = 0xFF16A34A;
    private static final int COLOR_STATUS_RED = 0xFFEF4444;
    private static final int COLOR_STATUS_RED_FOCUSED = 0xFFDC2626;
    private static final int COLOR_ICON_DEFAULT = 0xFFE4E4E7;
    private static final int COLOR_ACCENT_GREEN = 0xFF4ADE80;

    private final Activity activity;
    private final WidgetAddonManager manager;
    private final List<View> menuRows = new ArrayList<>();
    private final Set<String> installingIds = new HashSet<>();
    private final Set<String> installingUrls = new HashSet<>();

    private FrameLayout root;
    private LinearLayout secondary;
    private LinearLayout items;
    private TextView panelTitle;
    private TextView panelHint;
    private View triggerView;

    private boolean showing;
    private int state = MAIN;
    private int selectedMenu = 0;
    private int generation = 0;
    private String movingId = null;
    private List<WidgetAddon> catalog = new ArrayList<>();

    private AddonConfigServer configServer;
    private Bitmap qrBitmap;
    private ImageView qrImage;

    public WidgetManagerDrawer(Activity activity) {
        this.activity = activity;
        this.manager = WidgetAddonManager.getInstance(activity);
    }

    public boolean isShowing() {
        return showing;
    }

    public void show() {
        show(null);
    }

    public void show(View trigger) {
        if (showing) return;
        triggerView = trigger != null ? trigger : activity.getCurrentFocus();

        root = (FrameLayout) LayoutInflater.from(activity).inflate(R.layout.drawer_widget_manager, null);
        View main = root.findViewById(R.id.widgetManagerContent);
        int screen = activity.getResources().getDisplayMetrics().widthPixels;
        int rightWidth = Math.min(dp(380), (int) (screen * 0.45f));
        main.getLayoutParams().width = rightWidth;

        secondary = root.findViewById(R.id.widgetSubPanel);
        secondary.getLayoutParams().width = Math.min(dp(440), screen - rightWidth);
        panelTitle = root.findViewById(R.id.tvWidgetSubPanelTitle);
        panelHint = root.findViewById(R.id.tvWidgetSubPanelHint);
        items = root.findViewById(R.id.containerWidgetSubPanelItems);

        LinearLayout menu = root.findViewById(R.id.containerWidgetManagerItems);
        menu.removeAllViews();
        menuRows.clear();

        String[] titles = {"Sắp xếp và bật tắt", "Cửa hàng tiện ích", "Cài đặt từ điện thoại", "Thêm từ liên kết"};
        String[] hints = {"Thay đổi vị trí hoặc ẩn hiện hàng", "Duyệt kho tiện ích trực tuyến", "Quét mã QR kết nối nhanh", "Cài từ tệp cấu hình tiện ích"};
        int[] icons = {R.drawable.ic_widget_settings, R.drawable.ic_widget_widget, R.drawable.ic_widget_phone, R.drawable.ic_widget_link};

        for (int i = 0; i < titles.length; i++) {
            final int index = i;
            View row = createMasterMenuItem(index, titles[i], hints[i], icons[i]);
            menu.addView(row);
            menuRows.add(row);
        }

        activity.addContentView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (activity instanceof MainActivity) {
            ((MainActivity) activity).blockMainContentFocus();
        }

        showing = true;
        main.setTranslationX(rightWidth);
        main.animate().translationX(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();

        showMainMenu();
    }

    public void showMainMenu() {
        state = MAIN;
        generation++;
        movingId = null;
        stopConfigServer();
        hideKeyboard();

        if (secondary != null && secondary.getVisibility() == View.VISIBLE) {
            secondary.animate().cancel();
            int shift = secondary.getWidth() > 0 ? secondary.getWidth() : dp(440);
            secondary.animate().translationX(shift).setDuration(180)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator())
                    .withEndAction(() -> {
                        if (state == MAIN && secondary != null) {
                            secondary.setVisibility(View.GONE);
                        }
                    }).start();
        }

        paintMenu();
        if (!menuRows.isEmpty()) {
            View target = menuRows.get(Math.min(selectedMenu, menuRows.size() - 1));
            target.post(() -> target.requestFocus());
        }
    }

    public boolean handleBackPress() {
        if (movingId != null) {
            movingId = null;
            renderReorder();
            return true;
        }
        if (state == CONFIRM) {
            open(STORE);
            return true;
        }
        if (state != MAIN) {
            showMainMenu();
            return true;
        }
        dismiss();
        return false;
    }

    private void open(int next) {
        boolean opening = secondary.getVisibility() != View.VISIBLE || state == MAIN;
        secondary.animate().cancel();
        generation++;
        stopConfigServer();
        hideKeyboard();
        movingId = null;
        state = next;

        secondary.setVisibility(View.VISIBLE);
        int shift = secondary.getWidth() > 0 ? secondary.getWidth() : dp(440);
        secondary.setTranslationX(opening ? shift : 0f);
        if (opening) {
            secondary.animate().translationX(0f).setDuration(220)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .withEndAction(this::focusSubpanel).start();
        }

        switch (next) {
            case REORDER:
                renderReorder();
                break;
            case STORE:
                renderStore();
                final int request = generation;
                manager.fetchStoreCatalog((list, ok) -> {
                    if (!alive(request, STORE)) return;
                    catalog = list == null || list.isEmpty() ? manager.buildDefaultStoreCatalog() : list;
                    renderStore();
                });
                break;
            case QR:
                renderQr();
                break;
            case URL:
                renderUrl();
                break;
            default:
                showMainMenu();
                return;
        }

        if (!opening) {
            focusSubpanel();
        }
    }

    private void begin(String title, String hint) {
        panelTitle.setText(title);
        panelHint.setText(hint);
        items.removeAllViews();
        items.setGravity(Gravity.TOP);
    }

    private void renderStore() {
        begin("CỬA HÀNG TIỆN ÍCH", "Chọn tiện ích để cài đặt hoặc quản lý");
        List<WidgetAddon> list = new ArrayList<>(catalog.isEmpty() ? manager.buildDefaultStoreCatalog() : catalog);
        Set<String> known = new HashSet<>();
        for (WidgetAddon item : list) known.add(item.getId());
        for (WidgetAddon item : manager.getInstalledAddons()) {
            if (!item.isBuiltIn() && known.add(item.getId())) list.add(item);
        }

        if (list.isEmpty()) {
            TextView empty = text("Không có tiện ích nào.", 14, COLOR_TEXT_SECONDARY);
            empty.setPadding(dp(16), dp(24), dp(16), dp(16));
            items.addView(empty);
            return;
        }

        for (int i = 0; i < list.size(); i++) {
            WidgetAddon addon = list.get(i);
            boolean installed = manager.isInstalled(addon.getId());
            boolean busy = installingIds.contains(addon.getId());
            boolean builtIn = addon.isBuiltIn();

            View row = createStoreItemRow(addon, installed, busy, builtIn);
            items.addView(row);
        }
        restoreSubpanelFocus();
    }

    private View createStoreItemRow(WidgetAddon addon, boolean installed, boolean busy, boolean builtIn) {
        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.drawer_menu_item_bg);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding((int) (16 * density), (int) (12 * density), (int) (16 * density), (int) (12 * density));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (8 * density);
        row.setLayoutParams(lp);

        // Icon
        ImageView ivIcon = new ImageView(activity);
        int iconSize = (int) (26 * density);
        LinearLayout.LayoutParams lpIcon = new LinearLayout.LayoutParams(iconSize, iconSize);
        lpIcon.rightMargin = (int) (14 * density);
        ivIcon.setLayoutParams(lpIcon);
        ivIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);

        boolean hasRemoteIcon = false;
        String base = "";
        try { base = com.next.tv.launcher.security.NativeSecurityBridge.getProxyBaseUrl(activity); } catch (Throwable ignored) {}
        String iconUrl = addon.resolveIconUrl(base);
        if (!iconUrl.isEmpty()) {
            hasRemoteIcon = true;
            ivIcon.setBackground(background(0x22FFFFFF, 0x00000000, (int) (6 * density)));
            ivIcon.setPadding((int) (2 * density), (int) (2 * density), (int) (2 * density), (int) (2 * density));
            FastPosterLoader.getInstance().load(Uri.parse(iconUrl), ivIcon, fallbackIcon(addon));
        } else {
            ivIcon.setImageResource(fallbackIcon(addon));
            ivIcon.setColorFilter(COLOR_ICON_DEFAULT);
        }
        row.addView(ivIcon);

        // Văn bản: Tên & Mô tả
        LinearLayout textCol = new LinearLayout(activity);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvTitle = new TextView(activity);
        tvTitle.setText(addon.getName());
        tvTitle.setTextColor(COLOR_TEXT_PRIMARY);
        tvTitle.setTextSize(14f);
        tvTitle.setMaxLines(1);
        tvTitle.setEllipsize(TextUtils.TruncateAt.END);
        textCol.addView(tvTitle);

        TextView tvSub = new TextView(activity);
        String desc = addon.getDescription();
        if (desc == null || desc.trim().isEmpty()) {
            desc = builtIn ? "Tiện ích tích hợp sẵn của hệ thống" : "Tiện ích mở rộng";
        }
        tvSub.setText(desc);
        tvSub.setTextColor(COLOR_TEXT_SECONDARY);
        tvSub.setTextSize(11f);
        tvSub.setMaxLines(1);
        tvSub.setEllipsize(TextUtils.TruncateAt.END);
        textCol.addView(tvSub);

        row.addView(textCol);

        // Nút viên thuốc hành động to rõ ràng ở bên phải
        LinearLayout pill = new LinearLayout(activity);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding((int) (12 * density), (int) (6 * density), (int) (12 * density), (int) (6 * density));
        LinearLayout.LayoutParams lpPill = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, (int) (32 * density));
        lpPill.leftMargin = (int) (10 * density);
        pill.setLayoutParams(lpPill);

        ImageView ivAction = new ImageView(activity);
        int actIconSize = (int) (14 * density);
        LinearLayout.LayoutParams lpActIcon = new LinearLayout.LayoutParams(actIconSize, actIconSize);
        lpActIcon.rightMargin = (int) (6 * density);
        ivAction.setLayoutParams(lpActIcon);
        ivAction.setScaleType(ImageView.ScaleType.FIT_CENTER);
        pill.addView(ivAction);

        TextView tvAction = new TextView(activity);
        tvAction.setTextSize(12f);
        pill.addView(tvAction);

        row.addView(pill);

        Runnable updatePillState = () -> {
            boolean hasFocus = row.hasFocus();
            if (busy) {
                ivAction.setVisibility(View.GONE);
                tvAction.setText("Đang cài...");
                tvAction.setTextColor(hasFocus ? COLOR_TEXT_DESC_FOCUSED : COLOR_TEXT_MUTED);
                pill.setBackground(background(hasFocus ? 0x22000000 : 0x22FFFFFF, 0x00000000, (int) (8 * density)));
            } else if (!installed) {
                ivAction.setVisibility(View.VISIBLE);
                ivAction.setImageResource(R.drawable.ic_widget_install);
                tvAction.setText("Cài đặt");
                if (hasFocus) {
                    ivAction.setColorFilter(0xFFFFFFFF);
                    tvAction.setTextColor(0xFFFFFFFF);
                    pill.setBackground(background(0xFF16A34A, 0x00000000, (int) (8 * density)));
                } else {
                    ivAction.setColorFilter(COLOR_ACCENT_GREEN);
                    tvAction.setTextColor(COLOR_ACCENT_GREEN);
                    pill.setBackground(background(0x224ADE80, 0x444ADE80, (int) (8 * density)));
                }
            } else if (builtIn) {
                ivAction.setVisibility(View.VISIBLE);
                ivAction.setImageResource(R.drawable.ic_widget_enabled);
                tvAction.setText("Đã có sẵn");
                if (hasFocus) {
                    ivAction.setColorFilter(0xFFFFFFFF);
                    tvAction.setTextColor(0xFFFFFFFF);
                    pill.setBackground(background(0xFF16A34A, 0x00000000, (int) (8 * density)));
                } else {
                    ivAction.setColorFilter(COLOR_STATUS_GREEN);
                    tvAction.setTextColor(COLOR_STATUS_GREEN);
                    pill.setBackground(background(0x1F22C55E, 0x00000000, (int) (8 * density)));
                }
            } else {
                ivAction.setVisibility(View.VISIBLE);
                ivAction.setImageResource(R.drawable.ic_widget_remove);
                tvAction.setText("Gỡ bỏ");
                if (hasFocus) {
                    ivAction.setColorFilter(0xFFFFFFFF);
                    tvAction.setTextColor(0xFFFFFFFF);
                    pill.setBackground(background(0xFFDC2626, 0x00000000, (int) (8 * density)));
                } else {
                    ivAction.setColorFilter(COLOR_STATUS_RED);
                    tvAction.setTextColor(COLOR_STATUS_RED);
                    pill.setBackground(background(0x22EF4444, 0x44EF4444, (int) (8 * density)));
                }
            }
        };

        updatePillState.run();

        final boolean finalHasRemoteIcon = hasRemoteIcon;
        row.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate().scaleX(hasFocus ? 1.03f : 1.0f).scaleY(hasFocus ? 1.03f : 1.0f).setDuration(120).start();
            tvTitle.setTextColor(hasFocus ? COLOR_TEXT_FOCUSED : COLOR_TEXT_PRIMARY);
            tvSub.setTextColor(hasFocus ? COLOR_TEXT_DESC_FOCUSED : COLOR_TEXT_SECONDARY);
            if (!finalHasRemoteIcon) {
                ivIcon.setColorFilter(hasFocus ? COLOR_TEXT_FOCUSED : COLOR_ICON_DEFAULT);
            }
            updatePillState.run();
        });

        row.setOnClickListener(v -> {
            if (busy) return;
            if (!installed) {
                install(addon);
            } else if (builtIn) {
                toast("Tiện ích này đã được tích hợp sẵn");
            } else {
                confirmRemove(addon);
            }
        });

        row.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                showMainMenu();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_HOME) {
                dismiss();
                return true;
            }
            return false;
        });

        return row;
    }

    private void install(WidgetAddon addon) {
        if (!installingIds.add(addon.getId())) return;
        renderStore();
        manager.installFromStore(addon, (success, message, result) -> {
            installingIds.remove(addon.getId());
            if (!showing || activity.isDestroyed() || activity.isFinishing()) return;
            toast(success ? "Đã cài: " + addon.getName() : "Cài đặt thất bại: " + message);
            if (state == STORE) renderStore();
        });
    }

    private void confirmRemove(WidgetAddon addon) {
        state = CONFIRM;
        generation++;
        begin("GỠ TIỆN ÍCH", "Bạn muốn gỡ “" + addon.getName() + "” khỏi danh sách?");
        View cancel = actionCard("Hủy", "Giữ lại tiện ích này", R.drawable.ic_widget_back, false);
        View remove = actionCard("Gỡ tiện ích", "Xóa tiện ích khỏi TV", R.drawable.ic_widget_remove, true);

        cancel.setOnClickListener(v -> open(STORE));
        remove.setOnClickListener(v -> {
            boolean removed = manager.uninstallAddon(addon.getId());
            toast(removed ? "Đã gỡ: " + addon.getName() : "Không thể gỡ tiện ích");
            open(STORE);
        });

        items.addView(cancel);
        items.addView(remove);
        cancel.post(() -> cancel.requestFocus());
    }

    private void renderReorder() {
        begin("SẮP XẾP TIỆN ÍCH", movingId == null ? "Bấm OK để đổi vị trí · Giữ OK để bật/ẩn" : "Lên/Xuống để di chuyển · Bấm OK để lưu");
        List<WidgetAddon> list = manager.getInstalledAddons();
        if (list.isEmpty()) {
            TextView empty = text("Chưa có tiện ích nào được cài đặt.", 14, COLOR_TEXT_SECONDARY);
            empty.setPadding(dp(16), dp(24), dp(16), dp(16));
            items.addView(empty);
            return;
        }

        float density = activity.getResources().getDisplayMetrics().density;

        for (int i = 0; i < list.size(); i++) {
            final int index = i;
            final WidgetAddon addon = list.get(i);
            final boolean isBeingMoved = addon.getId().equals(movingId);
            final boolean isEnabled = addon.isEnabled();

            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.drawer_menu_item_bg);
            row.setFocusable(true);
            row.setClickable(true);
            row.setPadding((int) (16 * density), (int) (12 * density), (int) (16 * density), (int) (12 * density));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int) (8 * density);
            row.setLayoutParams(lp);

            ImageView ivIcon = new ImageView(activity);
            int iconSize = (int) (26 * density);
            LinearLayout.LayoutParams lpIcon = new LinearLayout.LayoutParams(iconSize, iconSize);
            lpIcon.rightMargin = (int) (14 * density);
            ivIcon.setLayoutParams(lpIcon);
            ivIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);

            boolean hasRemoteIcon = false;
            String base = "";
            try { base = com.next.tv.launcher.security.NativeSecurityBridge.getProxyBaseUrl(activity); } catch (Throwable ignored) {}
            String iconUrl = addon.resolveIconUrl(base);
            if (!iconUrl.isEmpty()) {
                hasRemoteIcon = true;
                ivIcon.setBackground(background(0x22FFFFFF, 0x00000000, (int) (6 * density)));
                ivIcon.setPadding((int) (2 * density), (int) (2 * density), (int) (2 * density), (int) (2 * density));
                FastPosterLoader.getInstance().load(Uri.parse(iconUrl), ivIcon, fallbackIcon(addon));
            } else {
                ivIcon.setImageResource(isBeingMoved ? R.drawable.ic_widget_move : fallbackIcon(addon));
                ivIcon.setColorFilter(COLOR_ICON_DEFAULT);
            }
            row.addView(ivIcon);

            LinearLayout textCol = new LinearLayout(activity);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvTitle = new TextView(activity);
            tvTitle.setText(addon.getName());
            tvTitle.setTextColor(isEnabled ? COLOR_TEXT_PRIMARY : COLOR_TEXT_MUTED);
            tvTitle.setTextSize(14f);
            tvTitle.setMaxLines(1);
            tvTitle.setEllipsize(TextUtils.TruncateAt.END);
            textCol.addView(tvTitle);

            TextView tvSub = new TextView(activity);
            tvSub.setTextSize(11f);
            tvSub.setMaxLines(1);
            tvSub.setEllipsize(TextUtils.TruncateAt.END);
            if (isBeingMoved) {
                tvSub.setText("Đang đổi vị trí (Lên/Xuống) · Bấm OK để lưu");
                tvSub.setTextColor(COLOR_ACCENT_CYAN);
            } else {
                tvSub.setText(isEnabled ? "Bấm OK để đổi vị trí · Giữ OK để ẩn" : "Bấm OK để đổi vị trí · Giữ OK để hiện");
                tvSub.setTextColor(COLOR_TEXT_SECONDARY);
            }
            textCol.addView(tvSub);

            row.addView(textCol);

            TextView tvStatus = new TextView(activity);
            tvStatus.setTextSize(12f);
            tvStatus.setPadding((int) (8 * density), 0, (int) (4 * density), 0);
            if (isBeingMoved) {
                tvStatus.setText("Đang đổi (▲/▼)");
                tvStatus.setTextColor(COLOR_ACCENT_CYAN);
            } else {
                tvStatus.setText(isEnabled ? "Hiện" : "Ẩn");
                tvStatus.setTextColor(isEnabled ? COLOR_STATUS_GREEN : COLOR_STATUS_RED);
            }
            row.addView(tvStatus);

            final boolean finalHasRemoteIcon = hasRemoteIcon;
            row.setOnFocusChangeListener((v, hasFocus) -> {
                v.animate().scaleX(hasFocus ? 1.03f : 1.0f).scaleY(hasFocus ? 1.03f : 1.0f).setDuration(120).start();
                tvTitle.setTextColor(hasFocus ? COLOR_TEXT_FOCUSED : (isEnabled ? COLOR_TEXT_PRIMARY : COLOR_TEXT_MUTED));
                tvSub.setTextColor(hasFocus ? (isBeingMoved ? COLOR_ACCENT_CYAN : COLOR_TEXT_DESC_FOCUSED) : (isBeingMoved ? COLOR_ACCENT_CYAN : COLOR_TEXT_SECONDARY));
                if (!finalHasRemoteIcon) {
                    ivIcon.setColorFilter(hasFocus ? COLOR_TEXT_FOCUSED : COLOR_ICON_DEFAULT);
                }
                if (isBeingMoved) {
                    tvStatus.setTextColor(COLOR_ACCENT_CYAN);
                } else {
                    tvStatus.setTextColor(hasFocus ? (isEnabled ? COLOR_STATUS_GREEN_FOCUSED : COLOR_STATUS_RED_FOCUSED) : (isEnabled ? COLOR_STATUS_GREEN : COLOR_STATUS_RED));
                }
            });

            row.setOnClickListener(v -> {
                if (movingId != null && movingId.equals(addon.getId())) {
                    movingId = null;
                    renderReorder();
                    focusSubpanelIndex(index);
                } else if (movingId != null) {
                    movingId = null;
                    renderReorder();
                    focusSubpanelIndex(index);
                } else {
                    movingId = addon.getId();
                    renderReorder();
                    focusSubpanelIndex(index);
                }
            });

            row.setOnLongClickListener(v -> {
                movingId = null;
                boolean nextState = !isEnabled;
                manager.toggleAddon(addon.getId(), nextState);
                toast((nextState ? "Đã bật: " : "Đã ẩn: ") + addon.getName());
                renderReorder();
                focusSubpanelIndex(index);
                return true;
            });

            row.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (isBeingMoved && (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN)) {
                    int target = index + (keyCode == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
                    if (target >= 0 && target < list.size()) {
                        manager.moveAddonOrder(index, target);
                        renderReorder();
                        focusSubpanelIndex(target);
                    }
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_BACK) {
                    if (movingId != null) {
                        movingId = null;
                        renderReorder();
                        focusSubpanelIndex(index);
                        return true;
                    }
                    showMainMenu();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_HOME) {
                    dismiss();
                    return true;
                }
                return false;
            });

            items.addView(row);
        }

        restoreSubpanelFocus();
    }

    private void renderQr() {
        begin("CÀI QUA ĐIỆN THOẠI", "Quét mã để cài đặt và sắp xếp tiện ích");
        items.setGravity(Gravity.CENTER_HORIZONTAL);
        configServer = new AddonConfigServer(activity);
        String token = String.format(Locale.US, "%06d", new SecureRandom().nextInt(900000) + 100000);
        configServer.setSessionToken(token);
        configServer.setEventListener(action -> {
            if (showing && state == QR) toast("Điện thoại đã cập nhật tiện ích");
        });
        configServer.startSafe();
        int port = configServer.getListeningPort();
        if (port <= 0) {
            items.addView(text("Không thể mở kết nối điện thoại. Hãy thử lại.", 14, COLOR_TEXT_MUTED));
            return;
        }

        String address = "http://" + DeviceIpAddress.get(activity) + ":" + port;
        int size = dp(180);
        qrBitmap = QrCodeGenerator.generate(address + "/?token=" + token, size);

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.setFocusable(true);
        card.setBackground(background(0x1F27272A, 0x33FFFFFF, dp(16)));
        card.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.02f : 1f).scaleY(focused ? 1.02f : 1f).setDuration(120).start();
            v.setBackground(background(focused ? 0x2E27272A : 0x1F27272A, focused ? 0xFFFFFFFF : 0x33FFFFFF, dp(16)));
        });

        card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                showMainMenu();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return true;
            if (keyCode == KeyEvent.KEYCODE_HOME) {
                dismiss();
                return true;
            }
            return false;
        });

        qrImage = new ImageView(activity);
        qrImage.setBackground(background(0xFFFFFFFF, 0x00000000, dp(12)));
        qrImage.setPadding(dp(10), dp(10), dp(10), dp(10));
        qrImage.setImageBitmap(qrBitmap);
        qrImage.setContentDescription("Mã QR kết nối điện thoại");
        card.addView(qrImage, new LinearLayout.LayoutParams(size + dp(20), size + dp(20)));

        TextView tvUrl = text(address, 14, COLOR_ACCENT_CYAN);
        tvUrl.setPadding(0, dp(14), 0, dp(6));
        tvUrl.setTextSize(14f);
        card.addView(tvUrl);

        TextView tvToken = text("Mã xác thực: " + token, 14, COLOR_TEXT_PRIMARY);
        tvToken.setTextSize(14f);
        card.addView(tvToken);

        TextView tvHint = text("Dùng cùng mạng Wi-Fi và quét mã bằng camera điện thoại.", 12, COLOR_TEXT_SECONDARY);
        tvHint.setGravity(Gravity.CENTER);
        tvHint.setPadding(0, dp(10), 0, 0);
        card.addView(tvHint);

        items.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.post(() -> card.requestFocus());
    }

    private void renderUrl() {
        begin("THÊM TỪ LIÊN KẾT", "Nhập địa chỉ tệp manifest.json của tiện ích");
        final int request = generation;
        float density = activity.getResources().getDisplayMetrics().density;

        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        input.setTextColor(COLOR_TEXT_PRIMARY);
        input.setHintTextColor(COLOR_TEXT_MUTED);
        input.setTextSize(14f);
        input.setHint("https://example.com/manifest.json");
        input.setPadding((int) (16 * density), (int) (14 * density), (int) (16 * density), (int) (14 * density));
        input.setBackground(background(0x1F27272A, 0x33FFFFFF, (int) (12 * density)));
        input.setId(R.id.widgetAddonUrlInput);
        input.setOnFocusChangeListener((v, focused) -> {
            input.setBackground(background(focused ? 0x2E27272A : 0x1F27272A, focused ? 0xFFFFFFFF : 0x33FFFFFF, (int) (12 * density)));
        });
        items.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (54 * density)));

        TextView status = text("", 12, COLOR_TEXT_MUTED);
        status.setPadding(0, (int) (10 * density), 0, (int) (10 * density));
        items.addView(status);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        View install = createActionPillButton("Cài đặt", R.drawable.ic_widget_install, true);
        View cancel = createActionPillButton("Hủy", R.drawable.ic_widget_back, false);
        install.setId(R.id.widgetAddonUrlInstall);
        cancel.setId(R.id.widgetAddonUrlCancel);

        LinearLayout.LayoutParams lpInstall = new LinearLayout.LayoutParams(0, (int) (50 * density), 1f);
        lpInstall.setMarginEnd((int) (10 * density));
        actions.addView(install, lpInstall);
        actions.addView(cancel, new LinearLayout.LayoutParams(0, (int) (50 * density), 1f));
        items.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        install.setNextFocusUpId(input.getId());
        cancel.setNextFocusUpId(input.getId());
        input.setNextFocusDownId(install.getId());

        install.setOnKeyListener((v, key, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (key == KeyEvent.KEYCODE_DPAD_RIGHT) { cancel.requestFocus(); return true; }
            if (key == KeyEvent.KEYCODE_DPAD_LEFT) return true;
            if (key == KeyEvent.KEYCODE_BACK) { showMainMenu(); return true; }
            if (key == KeyEvent.KEYCODE_HOME) { dismiss(); return true; }
            return false;
        });

        cancel.setOnKeyListener((v, key, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (key == KeyEvent.KEYCODE_DPAD_LEFT && install.isEnabled()) { install.requestFocus(); return true; }
            if (key == KeyEvent.KEYCODE_DPAD_RIGHT || key == KeyEvent.KEYCODE_BACK) { showMainMenu(); return true; }
            if (key == KeyEvent.KEYCODE_HOME) { dismiss(); return true; }
            return false;
        });

        cancel.setOnClickListener(v -> showMainMenu());
        install.setOnClickListener(v -> {
            String url = input.getText().toString().trim();
            Uri parsed = Uri.parse(url);
            if (!("https".equalsIgnoreCase(parsed.getScheme()) || "http".equalsIgnoreCase(parsed.getScheme())) || parsed.getHost() == null) {
                status.setText("Vui lòng nhập liên kết HTTP hoặc HTTPS hợp lệ.");
                input.requestFocus();
                return;
            }
            if (!installingUrls.add(url)) {
                status.setText("Liên kết này đang được cài đặt.");
                return;
            }
            hideKeyboard();
            input.setEnabled(false);
            install.setEnabled(false);
            status.setText("Đang tải và cài đặt tiện ích...");
            cancel.requestFocus();
            manager.installFromUrl(url, (success, message, addon) -> {
                installingUrls.remove(url);
                if (!alive(request, URL)) return;
                input.setEnabled(true);
                install.setEnabled(true);
                status.setText(success ? "Đã cài: " + (addon == null ? "tiện ích" : addon.getName()) : "Cài đặt thất bại: " + message);
                install.requestFocus();
            });
        });

        input.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard();
                install.requestFocus();
                return true;
            }
            return false;
        });

        input.setOnKeyListener((v, key, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    showMainMenu();
                    return true;
                }
                if (key == KeyEvent.KEYCODE_HOME) {
                    dismiss();
                    return true;
                }
            }
            return false;
        });

        input.post(() -> input.requestFocus());
    }

    private View createMasterMenuItem(int index, String title, String hint, int iconRes) {
        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.drawer_menu_item_bg);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding((int) (16 * density), (int) (12 * density), (int) (16 * density), (int) (12 * density));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (62 * density));
        lp.bottomMargin = (int) (8 * density);
        row.setLayoutParams(lp);

        ImageView ivIcon = new ImageView(activity);
        ivIcon.setImageResource(iconRes);
        int iconSize = (int) (22 * density);
        LinearLayout.LayoutParams lpIcon = new LinearLayout.LayoutParams(iconSize, iconSize);
        lpIcon.rightMargin = (int) (14 * density);
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
        tvSub.setText(hint);
        tvSub.setTextColor(COLOR_TEXT_SECONDARY);
        tvSub.setTextSize(11f);
        textCol.addView(tvSub);

        row.addView(textCol);

        row.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate().scaleX(hasFocus ? 1.03f : 1.0f).scaleY(hasFocus ? 1.03f : 1.0f).setDuration(120).start();
            tvTitle.setTextColor(hasFocus ? COLOR_TEXT_FOCUSED : COLOR_TEXT_PRIMARY);
            tvSub.setTextColor(hasFocus ? COLOR_TEXT_DESC_FOCUSED : COLOR_TEXT_SECONDARY);
            ivIcon.setColorFilter(hasFocus ? COLOR_TEXT_FOCUSED : COLOR_ICON_DEFAULT);
            if (!hasFocus && state != MAIN && index == selectedMenu) {
                v.setBackground(background(0x26FFFFFF, 0x80FFFFFF, dp(12)));
            } else if (!hasFocus) {
                v.setBackgroundResource(R.drawable.drawer_menu_item_bg);
            }
        });

        row.setOnClickListener(v -> {
            selectedMenu = index;
            open(index + 1);
            paintMenu();
        });

        row.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME) {
                dismiss();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                int target = Math.max(0, Math.min(menuRows.size() - 1, index + (keyCode == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1)));
                menuRows.get(target).requestFocus();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (state != MAIN && items != null && items.getChildCount() > 0) {
                    focusSubpanel();
                    return true;
                } else {
                    selectedMenu = index;
                    open(index + 1);
                    paintMenu();
                    return true;
                }
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                dismiss();
                return true;
            }
            return false;
        });

        return row;
    }

    private void paintMenu() {
        for (int i = 0; i < menuRows.size(); i++) {
            View row = menuRows.get(i);
            boolean isActive = (state != MAIN && i == selectedMenu);
            if (isActive && !row.hasFocus()) {
                row.setBackground(background(0x26FFFFFF, 0x80FFFFFF, dp(12)));
            } else if (!row.hasFocus()) {
                row.setBackgroundResource(R.drawable.drawer_menu_item_bg);
            }
        }
    }

    private void focusSubpanel() {
        if (items == null || items.getChildCount() == 0) return;
        View first = items.getChildAt(0);
        if (first != null) {
            first.post(() -> first.requestFocus());
        }
    }

    private void focusSubpanelIndex(int targetIndex) {
        if (items == null || items.getChildCount() == 0) return;
        int clamped = Math.max(0, Math.min(items.getChildCount() - 1, targetIndex));
        View target = items.getChildAt(clamped);
        if (target != null) {
            target.post(() -> target.requestFocus());
        }
    }

    private void restoreSubpanelFocus() {
        focusSubpanelIndex(0);
    }

    private View createActionPillButton(String text, int iconRes, boolean isAccent) {
        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout btn = new LinearLayout(activity);
        btn.setOrientation(LinearLayout.HORIZONTAL);
        btn.setGravity(Gravity.CENTER);
        btn.setFocusable(true);
        btn.setClickable(true);
        btn.setBackgroundResource(R.drawable.drawer_menu_item_bg);
        btn.setPadding((int) (14 * density), (int) (10 * density), (int) (14 * density), (int) (10 * density));

        ImageView iv = new ImageView(activity);
        iv.setImageResource(iconRes);
        int iconSize = (int) (18 * density);
        LinearLayout.LayoutParams lpIcon = new LinearLayout.LayoutParams(iconSize, iconSize);
        lpIcon.rightMargin = (int) (8 * density);
        iv.setLayoutParams(lpIcon);
        iv.setColorFilter(isAccent ? COLOR_ACCENT_GREEN : COLOR_ICON_DEFAULT);
        btn.addView(iv);

        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextSize(14f);
        tv.setTextColor(isAccent ? COLOR_ACCENT_GREEN : COLOR_TEXT_PRIMARY);
        btn.addView(tv);

        btn.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate().scaleX(hasFocus ? 1.03f : 1.0f).scaleY(hasFocus ? 1.03f : 1.0f).setDuration(120).start();
            tv.setTextColor(hasFocus ? COLOR_TEXT_FOCUSED : (isAccent ? COLOR_ACCENT_GREEN : COLOR_TEXT_PRIMARY));
            iv.setColorFilter(hasFocus ? COLOR_TEXT_FOCUSED : (isAccent ? COLOR_ACCENT_GREEN : COLOR_ICON_DEFAULT));
        });

        return btn;
    }

    private View actionCard(String title, String subtitle, int iconRes, boolean isDestructive) {
        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.drawer_menu_item_bg);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding((int) (16 * density), (int) (12 * density), (int) (16 * density), (int) (12 * density));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (60 * density));
        lp.bottomMargin = (int) (8 * density);
        row.setLayoutParams(lp);

        ImageView ivIcon = new ImageView(activity);
        ivIcon.setImageResource(iconRes);
        int iconSize = (int) (22 * density);
        LinearLayout.LayoutParams lpIcon = new LinearLayout.LayoutParams(iconSize, iconSize);
        lpIcon.rightMargin = (int) (14 * density);
        ivIcon.setLayoutParams(lpIcon);
        ivIcon.setColorFilter(isDestructive ? COLOR_STATUS_RED : COLOR_ICON_DEFAULT);
        row.addView(ivIcon);

        LinearLayout textCol = new LinearLayout(activity);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvTitle = new TextView(activity);
        tvTitle.setText(title);
        tvTitle.setTextColor(isDestructive ? COLOR_STATUS_RED : COLOR_TEXT_PRIMARY);
        tvTitle.setTextSize(14f);
        textCol.addView(tvTitle);

        TextView tvSub = new TextView(activity);
        tvSub.setText(subtitle);
        tvSub.setTextColor(COLOR_TEXT_SECONDARY);
        tvSub.setTextSize(11f);
        textCol.addView(tvSub);

        row.addView(textCol);

        row.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate().scaleX(hasFocus ? 1.03f : 1.0f).scaleY(hasFocus ? 1.03f : 1.0f).setDuration(120).start();
            tvTitle.setTextColor(hasFocus ? (isDestructive ? COLOR_STATUS_RED_FOCUSED : COLOR_TEXT_FOCUSED) : (isDestructive ? COLOR_STATUS_RED : COLOR_TEXT_PRIMARY));
            tvSub.setTextColor(hasFocus ? COLOR_TEXT_DESC_FOCUSED : COLOR_TEXT_SECONDARY);
            ivIcon.setColorFilter(hasFocus ? (isDestructive ? COLOR_STATUS_RED_FOCUSED : COLOR_TEXT_FOCUSED) : (isDestructive ? COLOR_STATUS_RED : COLOR_ICON_DEFAULT));
        });

        row.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                open(STORE);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return true;
            if (keyCode == KeyEvent.KEYCODE_HOME) {
                dismiss();
                return true;
            }
            return false;
        });

        return row;
    }

    private int fallbackIcon(WidgetAddon addon) {
        String id = addon.getId().toLowerCase(Locale.ROOT);
        if (id.contains("weather")) return R.drawable.ic_widget_sun;
        if (id.contains("sport")) return R.drawable.ic_widget_football;
        if (id.contains("music") || id.contains("media")) return R.drawable.ic_widget_music;
        if (id.contains("time") || id.contains("calendar") || id.contains("usage")) return R.drawable.ic_widget_clock;
        if (id.contains("news")) return R.drawable.ic_widget_news;
        if (id.contains("youtube") || id.contains("video")) return R.drawable.ic_widget_video;
        if (id.contains("gold")) return R.drawable.ic_widget_gold;
        if (id.contains("crypto")) return R.drawable.ic_widget_currency;
        return R.drawable.ic_widget_widget;
    }

    private TextView text(String value, int size, int color) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        return text;
    }

    private GradientDrawable background(int color, int border, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (border != 0) {
            drawable.setStroke(dp(1), border);
        }
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }

    private boolean alive(int request, int expected) {
        return showing && generation == request && state == expected && !activity.isFinishing() && !activity.isDestroyed();
    }

    private void hideKeyboard() {
        if (root == null) return;
        InputMethodManager keyboard = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(root.getWindowToken(), 0);
    }

    private void stopConfigServer() {
        if (qrImage != null) {
            qrImage.setImageBitmap(null);
            qrImage = null;
        }
        if (qrBitmap != null) {
            if (!qrBitmap.isRecycled()) qrBitmap.recycle();
            qrBitmap = null;
        }
        if (configServer != null) {
            configServer.stopSafe();
            configServer = null;
        }
    }

    public void dismiss() {
        if (!showing) return;
        hideKeyboard();
        stopConfigServer();
        generation++;
        showing = false;

        if (root != null && root.getParent() instanceof ViewGroup) {
            ((ViewGroup) root.getParent()).removeView(root);
        }
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).unblockMainContentFocus();
        }
        if (triggerView != null && triggerView.isShown()) {
            triggerView.requestFocus();
        } else if (activity instanceof MainActivity) {
            ((MainActivity) activity).focusUtilityType(UtilitySectionAdapter.TYPE_WIDGET_MANAGER);
        }
        root = null;
        secondary = null;
        items = null;
        menuRows.clear();
    }
}
