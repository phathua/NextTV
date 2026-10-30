package com.next.tv.launcher.widget;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.next.tv.launcher.MainActivity;
import com.next.tv.launcher.R;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;

public final class WidgetManagerDrawer {

    private static final int STATE_MAIN = 0;
    private static final int STATE_REORDER = 1;
    private static final int STATE_STORE = 2;
    private static final int STATE_QR = 3;

    private final Activity activity;
    private View drawerView;
    private View drawerContent;
    private TextView tvTitle;
    private TextView tvSubtitle;
    private LinearLayout containerItems;

    private boolean isShowing = false;
    private int currentState = STATE_MAIN;
    private View triggerView;

    private AddonConfigServer addonConfigServer;
    private Bitmap qrBitmap;
    private ImageView ivQr;
    private int movingWidgetIndex = -1;
    private int lastSelectedMainMenuIndex = 0;

    public WidgetManagerDrawer(Activity activity) {
        this.activity = activity;
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
        drawerView = inflater.inflate(R.layout.drawer_widget_manager, null);
        drawerContent = drawerView.findViewById(R.id.widgetManagerContent);
        tvTitle = drawerView.findViewById(R.id.tvWidgetManagerTitle);
        tvSubtitle = drawerView.findViewById(R.id.tvWidgetManagerSub);
        containerItems = drawerView.findViewById(R.id.containerWidgetManagerItems);

        drawerView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    handleBackPress();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_HOME) {
                    dismiss();
                    return true;
                }
            }
            return false;
        });

        showMainMenu();

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
    }

    private void handleBackPress() {
        if (movingWidgetIndex != -1) {
            movingWidgetIndex = -1;
            refreshWidgetReorderList();
            return;
        }
        if (currentState != STATE_MAIN) {
            stopConfigServer();
            showMainMenu();
        } else {
            dismiss();
        }
    }

    public void showMainMenu() {
        currentState = STATE_MAIN;
        movingWidgetIndex = -1;
        stopConfigServer();

        if (tvTitle != null) tvTitle.setText("QUẢN LÝ TIỆN ÍCH");
        if (tvSubtitle != null) tvSubtitle.setText("Tùy chỉnh và mở rộng tiện ích");
        if (containerItems == null) return;
        containerItems.removeAllViews();

        addMenuNavigationRow("Sắp xếp và bật tắt", "Thay đổi vị trí hoặc ẩn hiện", R.drawable.ic_widget_settings, 0, this::openWidgetReorderScreen);
        addMenuNavigationRow("Cửa hàng tiện ích", "Duyệt kho tiện ích trực tuyến", R.drawable.ic_layers, 1, this::openWidgetStoreScreen);
        addMenuNavigationRow("Cài đặt từ điện thoại", "Quét mã QR kết nối nhanh", R.drawable.ic_mode_tuning, 2, this::openWidgetQrScreen);
        addMenuNavigationRow("Thêm từ liên kết", "Cài đặt bằng đường dẫn tệp cấu hình", R.drawable.ic_arrow_right, 3, this::showAddonUrlDialog);

        containerItems.post(() -> {
            if (containerItems.getChildCount() > 0) {
                int targetIdx = Math.min(lastSelectedMainMenuIndex, containerItems.getChildCount() - 1);
                View target = containerItems.getChildAt(targetIdx >= 0 ? targetIdx : 0);
                if (target != null) target.requestFocus();
            }
        });
    }

    private void openWidgetReorderScreen() {
        currentState = STATE_REORDER;
        movingWidgetIndex = -1;
        if (tvTitle != null) tvTitle.setText("SẮP XẾP TIỆN ÍCH");
        if (tvSubtitle != null) tvSubtitle.setText("Bấm OK để đổi vị trí, giữ để bật tắt");
        refreshWidgetReorderList();
    }

    private void refreshWidgetReorderList() {
        if (containerItems == null) return;
        containerItems.removeAllViews();
        float density = activity.getResources().getDisplayMetrics().density;

        addBackNavigationRow("Quay lại danh mục", this::showMainMenu);

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

            TextView tvItemTitle = new TextView(activity);
            tvItemTitle.setText(addon.getName());
            tvItemTitle.setTextColor(Color.parseColor(addon.isEnabled() ? "#F4F4F5" : "#71717A"));
            tvItemTitle.setTextSize(13f);
            textCol.addView(tvItemTitle);

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
                    refreshWidgetReorderList();
                } else if (movingWidgetIndex != -1) {
                    movingWidgetIndex = -1;
                    addonMgr.saveOrderFromList(list);
                    refreshWidgetReorderList();
                } else {
                    movingWidgetIndex = index;
                    refreshWidgetReorderList();
                }
            });

            row.setOnLongClickListener(v -> {
                boolean newState = !addon.isEnabled();
                addonMgr.toggleAddon(addon.getId(), newState);
                refreshWidgetReorderList();
                Toast.makeText(activity, (newState ? "Đã bật: " : "Đã tắt: ") + addon.getName(), Toast.LENGTH_SHORT).show();
                return true;
            });

            row.setOnFocusChangeListener((v, hasFocus) -> {
                v.animate().scaleX(hasFocus ? 1.03f : 1.0f).scaleY(hasFocus ? 1.03f : 1.0f).setDuration(120).start();
                tvItemTitle.setTextColor(Color.parseColor(hasFocus ? "#09090B" : (addon.isEnabled() ? "#F4F4F5" : "#71717A")));
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
                            refreshWidgetReorderList();
                        }
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && movingWidgetIndex == index) {
                        if (index < list.size() - 1) {
                            addonMgr.moveAddonOrder(index, index + 1);
                            movingWidgetIndex = index + 1;
                            refreshWidgetReorderList();
                        }
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        handleBackPress();
                        return true;
                    }
                }
                return false;
            });

            containerItems.addView(row);
        }

        if (containerItems.getChildCount() > 1) {
            View target = containerItems.getChildAt(movingWidgetIndex >= 0 ? movingWidgetIndex + 1 : 1);
            if (target != null) target.post(() -> target.requestFocus());
        }
    }

    private void openWidgetStoreScreen() {
        currentState = STATE_STORE;
        if (tvTitle != null) tvTitle.setText("CỬA HÀNG TIỆN ÍCH");
        if (tvSubtitle != null) tvSubtitle.setText("Duyệt và cài đặt tiện ích");
        if (containerItems == null) return;
        containerItems.removeAllViews();

        float density = activity.getResources().getDisplayMetrics().density;
        addBackNavigationRow("Quay lại danh mục", this::showMainMenu);

        TextView tvLoading = new TextView(activity);
        tvLoading.setText("Đang nạp danh mục trực tuyến...");
        tvLoading.setTextColor(Color.parseColor("#71717A"));
        tvLoading.setTextSize(13f);
        tvLoading.setPadding((int) (14 * density), (int) (16 * density), (int) (14 * density), (int) (16 * density));
        containerItems.addView(tvLoading);

        WidgetAddonManager addonMgr = WidgetAddonManager.getInstance(activity);
        addonMgr.fetchStoreCatalog((catalog, success) -> {
            while (containerItems.getChildCount() > 1) {
                containerItems.removeViewAt(1);
            }
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

                TextView tvItemTitle = new TextView(activity);
                tvItemTitle.setText(addon.getName());
                tvItemTitle.setTextColor(Color.parseColor("#F4F4F5"));
                tvItemTitle.setTextSize(13f);
                textCol.addView(tvItemTitle);

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
                            openWidgetStoreScreen();
                            Toast.makeText(activity, "Đã gỡ tiện ích: " + addon.getName(), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(activity, "Tiện ích hệ thống mặc định", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        addonMgr.installFromStore(addon, (ok, msg, a) -> {
                            openWidgetStoreScreen();
                            Toast.makeText(activity, "Đã cài đặt: " + addon.getName(), Toast.LENGTH_SHORT).show();
                        });
                    }
                });

                row.setOnFocusChangeListener((v, hasFocus) -> {
                    v.animate().scaleX(hasFocus ? 1.03f : 1.0f).scaleY(hasFocus ? 1.03f : 1.0f).setDuration(120).start();
                    tvItemTitle.setTextColor(Color.parseColor(hasFocus ? "#09090B" : "#F4F4F5"));
                    tvDesc.setTextColor(Color.parseColor(hasFocus ? "#52525B" : "#71717A"));
                    tvAction.setTextColor(Color.parseColor(hasFocus ? "#09090B" : (isInst ? "#71717A" : "#38BDF8")));
                });

                row.setOnKeyListener((v, keyCode, event) -> {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        if (keyCode == KeyEvent.KEYCODE_HOME) {
                            dismiss();
                            return true;
                        }
                        if (keyCode == KeyEvent.KEYCODE_BACK) {
                            handleBackPress();
                            return true;
                        }
                    }
                    return false;
                });

                containerItems.addView(row);
            }

            if (containerItems.getChildCount() > 1) {
                View targetRow = containerItems.getChildAt(1);
                if (targetRow != null) targetRow.post(() -> targetRow.requestFocus());
            }
        });
    }

    private void openWidgetQrScreen() {
        currentState = STATE_QR;
        if (tvTitle != null) tvTitle.setText("CÀI QUA ĐIỆN THOẠI");
        if (tvSubtitle != null) tvSubtitle.setText("Quét mã để điều khiển nhanh");
        if (containerItems == null) return;
        containerItems.removeAllViews();

        float density = activity.getResources().getDisplayMetrics().density;
        addBackNavigationRow("Quay lại danh mục", () -> {
            stopConfigServer();
            showMainMenu();
        });

        stopConfigServer();
        addonConfigServer = new AddonConfigServer(activity);
        String sessionToken = String.format(Locale.US, "%06d", new SecureRandom().nextInt(900000) + 100000);
        addonConfigServer.setSessionToken(sessionToken);
        addonConfigServer.setEventListener(action -> {
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
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    stopConfigServer();
                    showMainMenu();
                    return true;
                }
            }
            return false;
        });

        containerItems.addView(card);
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

        TextView tvBack = new TextView(activity);
        tvBack.setText("< " + title);
        tvBack.setTextColor(Color.parseColor("#38BDF8"));
        tvBack.setTextSize(13f);
        tvBack.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(tvBack);

        row.setOnClickListener(v -> {
            if (onClick != null) onClick.run();
        });

        row.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate().scaleX(hasFocus ? 1.03f : 1.0f).scaleY(hasFocus ? 1.03f : 1.0f).setDuration(120).start();
            tvBack.setTextColor(Color.parseColor(hasFocus ? "#09090B" : "#38BDF8"));
        });

        row.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_HOME) {
                    dismiss();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if (onClick != null) onClick.run();
                    return true;
                }
            }
            return false;
        });

        containerItems.addView(row);
    }

    private void addMenuNavigationRow(String title, String subtitle, int iconRes, int index, Runnable onClick) {
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

        TextView tvItemTitle = new TextView(activity);
        tvItemTitle.setText(title);
        tvItemTitle.setTextColor(Color.parseColor("#F4F4F5"));
        tvItemTitle.setTextSize(14f);
        textCol.addView(tvItemTitle);

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
            lastSelectedMainMenuIndex = index;
            if (onClick != null) onClick.run();
        });

        row.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate().scaleX(hasFocus ? 1.03f : 1.0f).scaleY(hasFocus ? 1.03f : 1.0f).setDuration(120).start();
            tvItemTitle.setTextColor(Color.parseColor(hasFocus ? "#09090B" : "#F4F4F5"));
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
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    dismiss();
                    return true;
                }
            }
            return false;
        });

        containerItems.addView(row);
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

    public void dismiss() {
        if (!isShowing || drawerView == null) return;
        stopConfigServer();

        if (activity instanceof MainActivity) {
            ((MainActivity) activity).unblockMainContentFocus();
            ((MainActivity) activity).focusUtilityItem(1);
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
