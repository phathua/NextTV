package com.next.tv.launcher.remote;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.widget.Button;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.next.tv.launcher.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RemoteControlDrawer {
    private final Activity activity;
    private final Runnable onStateChangedListener;
    private final KeyRemapManager remapManager;
    private final WebRemoteServer webRemoteServer;

    private View drawerView;
    private View drawerContent;
    private boolean isShowing = false;
    private View previousFocusView;
    private String listeningActionId = null;
    private TextView listeningBadgeView = null;
    private String previousBadgeText = null;
    private int previousBadgeColor = 0;

    // Left Floating QR Card
    private View remoteSubPanel;
    private LinearLayout containerRemoteSubPanelItems;
    private View containerPermAccess;
    private boolean isSubPanelOpen = false;

    private View cardWebRemoteQrFloat;
    private ImageView imgWebRemoteQrFloat;
    private TextView txtWebRemoteUrlFloat;

    // Sidebar views
    private TextView txtBadgeAccess;
    private Switch switchWebRemote;
    private LinearLayout containerKeyItems;

    public RemoteControlDrawer(Activity activity, Runnable onStateChanged) {
        this.activity = activity;
        this.onStateChangedListener = onStateChanged;
        this.remapManager = KeyRemapManager.getInstance(activity);
        this.webRemoteServer = WebRemoteServer.getInstance(activity);
    }

    public boolean isShowing() {
        return isShowing;
    }

    public void show() {
        show(null);
    }

    public void show(View triggerView) {
        if (isShowing && drawerView != null && drawerView.getParent() != null) {
            View firstFocus = drawerView.findViewById(R.id.rowWebRemoteToggle);
            if (firstFocus != null) firstFocus.requestFocus();
            return;
        }
        previousFocusView = triggerView != null ? triggerView : activity.getCurrentFocus();
        isShowing = false;

        LayoutInflater inflater = LayoutInflater.from(activity);
        drawerView = inflater.inflate(R.layout.drawer_remote_control, null);
        drawerContent = drawerView.findViewById(R.id.remoteControlContent);

        bindViews(drawerView);

        if (activity instanceof com.next.tv.launcher.MainActivity) {
            ((com.next.tv.launcher.MainActivity) activity).blockMainContentFocus();
        }

        activity.addContentView(drawerView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        isShowing = true;

        if (drawerContent != null) {
            drawerContent.setTranslationX(400f);
            drawerContent.animate().translationX(0f).setDuration(220).start();
        }

        drawerView.postDelayed(() -> {
            if (isShowing && drawerView != null) {
                View firstFocus = drawerView.findViewById(R.id.rowWebRemoteToggle);
                if (firstFocus != null) {
                    firstFocus.requestFocus();
                }
            }
        }, 100);

        View.OnKeyListener dismissOnBackOrLeft = (v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if (listeningActionId != null) {
                        cancelListeningMode();
                        return true;
                    }
                    if (isSubPanelOpen) {
                        closeAppPickerSubPanel();
                        return true;
                    }
                    dismiss();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (isSubPanelOpen) {
                        if (containerRemoteSubPanelItems != null && containerRemoteSubPanelItems.getChildCount() > 0) {
                            containerRemoteSubPanelItems.getChildAt(0).requestFocus();
                        }
                        return true;
                    }
                    dismiss();
                    return true;
                }
            }
            return false;
        };

        View rowPermAccess = drawerView.findViewById(R.id.rowPermAccess);
        View rowWebRemoteToggle = drawerView.findViewById(R.id.rowWebRemoteToggle);

        if (rowPermAccess != null) rowPermAccess.setOnKeyListener(dismissOnBackOrLeft);
        if (rowWebRemoteToggle != null) rowWebRemoteToggle.setOnKeyListener(dismissOnBackOrLeft);

        View root = drawerView.findViewById(R.id.remoteControlRoot);
        if (root != null) {
            root.setOnClickListener(v -> dismiss());
        }
    }

    public void dismiss() {
        if (!isShowing || drawerView == null) return;
        cancelListeningMode();
        isShowing = false;
        if (activity instanceof com.next.tv.launcher.MainActivity) {
            ((com.next.tv.launcher.MainActivity) activity).unblockMainContentFocus();
        }

        if (activity instanceof com.next.tv.launcher.MainActivity) {
            ((com.next.tv.launcher.MainActivity) activity).focusUtilityItem(4);
        } else if (previousFocusView != null && previousFocusView.isShown()) {
            previousFocusView.post(() -> previousFocusView.requestFocus());
        }

        if (cardWebRemoteQrFloat != null && cardWebRemoteQrFloat.getVisibility() == View.VISIBLE) {
            cardWebRemoteQrFloat.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(160).start();
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

        if (onStateChangedListener != null) {
            onStateChangedListener.run();
        }
    }

    private void bindViews(View root) {
        remoteSubPanel = root.findViewById(R.id.remoteSubPanel);
        containerRemoteSubPanelItems = root.findViewById(R.id.containerRemoteSubPanelItems);
        containerPermAccess = root.findViewById(R.id.containerPermAccess);
        isSubPanelOpen = false;

        // Floating QR Card
        cardWebRemoteQrFloat = root.findViewById(R.id.cardWebRemoteQrFloat);
        imgWebRemoteQrFloat = root.findViewById(R.id.imgWebRemoteQrFloat);
        txtWebRemoteUrlFloat = root.findViewById(R.id.txtWebRemoteUrlFloat);

        // Sidebar
        View rowWebRemoteToggle = root.findViewById(R.id.rowWebRemoteToggle);
        View rowPermAccess = root.findViewById(R.id.rowPermAccess);

        txtBadgeAccess = root.findViewById(R.id.txtBadgeAccess);
        updateAccessBadge();

        rowPermAccess.setOnClickListener(v -> {
            try {
                activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Exception ignored) {}
        });

        // Web Remote switch
        switchWebRemote = root.findViewById(R.id.switchWebRemote);

        boolean isRunning = webRemoteServer.isRunning();
        switchWebRemote.setChecked(isRunning);
        updateWebRemoteUi(isRunning);

        rowWebRemoteToggle.setOnClickListener(v -> {
            boolean next = !switchWebRemote.isChecked();
            if (next && !isAccessibilityEnabled()) {
                Toast.makeText(activity, "Cần cấp quyền Trợ năng", Toast.LENGTH_SHORT).show();
                try {
                    activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                } catch (Exception ignored) {}
                return;
            }
            switchWebRemote.setChecked(next);
            if (next) {
                webRemoteServer.start();
            } else {
                webRemoteServer.stop();
            }
            updateWebRemoteUi(next);
            if (onStateChangedListener != null) onStateChangedListener.run();
        });

        // Inverted Focus on toggles
        applyInvertedFocus(rowWebRemoteToggle,
                getFirstImageView(rowWebRemoteToggle),
                getFirstTextView(rowWebRemoteToggle),
                null, null);

        applyInvertedFocus(rowPermAccess,
                getFirstImageView(rowPermAccess),
                getFirstTextView(rowPermAccess),
                null,
                txtBadgeAccess);

        containerKeyItems = root.findViewById(R.id.containerKeyItems);
        populateActionItems();
    }

    private ImageView getFirstImageView(View parent) {
        if (parent instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) parent;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View child = vg.getChildAt(i);
                if (child instanceof ImageView) return (ImageView) child;
            }
        }
        return null;
    }

    private TextView getFirstTextView(View parent) {
        if (parent instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) parent;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View child = vg.getChildAt(i);
                if (child instanceof TextView) return (TextView) child;
                if (child instanceof ViewGroup) {
                    TextView tv = getFirstTextView(child);
                    if (tv != null) return tv;
                }
            }
        }
        return null;
    }

    private void applyInvertedFocus(View row, ImageView icon, TextView title, TextView subtitle, TextView badge) {
        applyInvertedFocus(row, icon, title, subtitle, badge, false);
    }

    private void applyInvertedFocus(View row, ImageView icon, TextView title, TextView subtitle, TextView badge, boolean isAppIcon) {
        if (row == null) return;
        row.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate().scaleX(hasFocus ? 1.01f : 1.0f).scaleY(hasFocus ? 1.02f : 1.0f).setDuration(120).start();
            if (icon != null && !isAppIcon) {
                icon.setImageTintList(hasFocus ? ColorStateList.valueOf(Color.parseColor("#09090B")) : null);
            }
            if (title != null) {
                title.setTextColor(Color.parseColor(hasFocus ? "#09090B" : "#FFFFFFFF"));
            }
            if (subtitle != null) {
                subtitle.setTextColor(Color.parseColor(hasFocus ? "#52525B" : "#80FFFFFF"));
            }
            if (badge != null) {
                if (hasFocus) {
                    if (badge == txtBadgeAccess) {
                        badge.setTextColor(Color.parseColor("#09090B"));
                    } else {
                        String txt = badge.getText().toString();
                        if (txt.equals("Bấm để gán")) {
                            badge.setTextColor(Color.parseColor("#52525B"));
                        } else {
                            badge.setTextColor(Color.parseColor("#059669"));
                        }
                    }
                } else {
                    if (badge == txtBadgeAccess) {
                        updateAccessBadge();
                    } else {
                        String txt = badge.getText().toString();
                        if (txt.equals("Bấm để gán")) {
                            badge.setTextColor(Color.parseColor("#71717A"));
                        } else {
                            badge.setTextColor(Color.parseColor("#10B981"));
                        }
                    }
                }
            }
            if (hasFocus && isSubPanelOpen && !isDescendantOf(remoteSubPanel, v)) {
                closeAppPickerSubPanel();
            }
        });
    }

    private void updateAccessBadge() {
        boolean enabled = isAccessibilityEnabled();
        if (containerPermAccess != null) {
            containerPermAccess.setVisibility(enabled ? View.GONE : View.VISIBLE);
        }
        if (txtBadgeAccess == null) return;
        if (enabled) {
            txtBadgeAccess.setText("Đã cấp");
            txtBadgeAccess.setTextColor(Color.parseColor("#10B981"));
        } else {
            txtBadgeAccess.setText("Mở cài đặt");
            txtBadgeAccess.setTextColor(Color.parseColor("#FFB340"));
        }
    }

    private boolean isAccessibilityEnabled() {
        try {
            String services = Settings.Secure.getString(activity.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return services != null && services.contains(activity.getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    private void updateWebRemoteUi(boolean running) {
        if (cardWebRemoteQrFloat == null) return;
        if (running) {
            String url = webRemoteServer.getUrl();
            txtWebRemoteUrlFloat.setText(url);
            Bitmap qr = QrCodeMatrixGenerator.generateQrBitmap(url, 440);
            if (qr != null) {
                imgWebRemoteQrFloat.setImageBitmap(qr);
            }
            if (cardWebRemoteQrFloat.getVisibility() != View.VISIBLE) {
                cardWebRemoteQrFloat.setVisibility(View.VISIBLE);
                cardWebRemoteQrFloat.setAlpha(0f);
                cardWebRemoteQrFloat.setScaleX(0.92f);
                cardWebRemoteQrFloat.setScaleY(0.92f);
                cardWebRemoteQrFloat.animate().alpha(1f).scaleX(1.0f).scaleY(1.0f).setDuration(220).start();
            }
        } else {
            if (cardWebRemoteQrFloat.getVisibility() == View.VISIBLE) {
                cardWebRemoteQrFloat.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(160)
                        .withEndAction(() -> cardWebRemoteQrFloat.setVisibility(View.GONE)).start();
            }
        }
    }

    private void populateActionItems() {
        if (containerKeyItems == null) return;
        containerKeyItems.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(activity);
        PackageManager pm = activity.getPackageManager();

        // 1. System Action Items
        for (KeyRemapManager.ActionDef actionDef : KeyRemapManager.getDefaultActionDefs()) {
            View row = inflater.inflate(R.layout.item_remap_key_row, containerKeyItems, false);
            ImageView icon = row.findViewById(R.id.imgKeyGroupIcon);
            TextView title = row.findViewById(R.id.txtKeyTitle);
            TextView badge = row.findViewById(R.id.txtKeyActionBadge);

            icon.setImageResource(actionDef.iconRes);
            title.setText(actionDef.title);

            KeyRemapManager.KeyBinding kb = remapManager.getKeyBindingForAction(actionDef.id);
            if (kb != null && (kb.scanCode > 0 || kb.keyCode > 0)) {
                badge.setText("[" + kb.keyName + "]");
                badge.setTextColor(Color.parseColor("#10B981"));
            } else {
                badge.setText("Bấm để gán");
                badge.setTextColor(Color.parseColor("#71717A"));
            }

            applyInvertedFocus(row, icon, title, null, badge, true);

            row.setOnClickListener(v -> {
                if (!isAccessibilityEnabled()) {
                    Toast.makeText(activity, "Cần cấp quyền Trợ năng", Toast.LENGTH_SHORT).show();
                    try {
                        activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    } catch (Exception ignored) {}
                    return;
                }
                startInlineListening(actionDef, null, actionDef.title, badge, row);
            });

            row.setOnLongClickListener(v -> {
                KeyRemapManager.KeyBinding binding = remapManager.getKeyBindingForAction(actionDef.id);
                if (binding != null && (binding.scanCode > 0 || binding.keyCode > 0)) {
                    cancelListeningMode();
                    remapManager.unassignAction(actionDef.id);
                    badge.setText("Bấm để gán");
                    badge.setTextColor(row.hasFocus() ? Color.parseColor("#52525B") : Color.parseColor("#71717A"));
                    Toast.makeText(activity, "Đã xóa gán", Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            });

            row.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        if (listeningActionId != null) {
                            cancelListeningMode();
                            return true;
                        }
                        dismiss();
                        return true;
                    }
                }
                return false;
            });

            containerKeyItems.addView(row);
        }

        // 2. Custom Mapped Apps
        for (KeyRemapManager.ActionDef appDef : remapManager.getAllAppActionDefs()) {
            View row = inflater.inflate(R.layout.item_remap_key_row, containerKeyItems, false);
            ImageView icon = row.findViewById(R.id.imgKeyGroupIcon);
            TextView title = row.findViewById(R.id.txtKeyTitle);
            TextView badge = row.findViewById(R.id.txtKeyActionBadge);

            String pkg = remapManager.getActionPayload(appDef.id);
            try {
                Drawable appIcon = pm.getApplicationIcon(pkg);
                icon.setImageDrawable(appIcon);
                icon.setImageTintList(null);
                icon.clearColorFilter();
            } catch (Exception e) {
                icon.setImageResource(R.drawable.ic_apps);
            }
            title.setText(appDef.title);

            KeyRemapManager.KeyBinding kb = remapManager.getKeyBindingForAction(appDef.id);
            if (kb != null && (kb.scanCode > 0 || kb.keyCode > 0)) {
                badge.setText("[" + kb.keyName + "]");
                badge.setTextColor(Color.parseColor("#10B981"));
            } else {
                badge.setText("Bấm để gán");
                badge.setTextColor(Color.parseColor("#71717A"));
            }

            applyInvertedFocus(row, icon, title, null, badge);

            row.setOnClickListener(v -> {
                if (!isAccessibilityEnabled()) {
                    Toast.makeText(activity, "Cần cấp quyền Trợ năng", Toast.LENGTH_SHORT).show();
                    try {
                        activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    } catch (Exception ignored) {}
                    return;
                }
                startInlineListening(appDef, pkg, appDef.title, badge, row);
            });

            row.setOnLongClickListener(v -> {
                remapManager.unassignAction(appDef.id);
                populateActionItems();
                Toast.makeText(activity, "Đã xóa gán " + appDef.title, Toast.LENGTH_SHORT).show();
                return true;
            });

            row.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        if (listeningActionId != null) {
                            cancelListeningMode();
                            return true;
                        }
                        dismiss();
                        return true;
                    }
                }
                return false;
            });

            containerKeyItems.addView(row);
        }

        // 3. [+ Gán thêm ứng dụng] Row
        View addRow = inflater.inflate(R.layout.item_remap_key_row, containerKeyItems, false);
        ImageView addIcon = addRow.findViewById(R.id.imgKeyGroupIcon);
        TextView addTitle = addRow.findViewById(R.id.txtKeyTitle);
        TextView addBadge = addRow.findViewById(R.id.txtKeyActionBadge);

        addIcon.setImageResource(R.drawable.ic_apps);
        addTitle.setText("Gán thêm ứng dụng");
        addBadge.setText("Chọn app");
        addBadge.setTextColor(Color.parseColor("#38BDF8"));

        applyInvertedFocus(addRow, addIcon, addTitle, null, addBadge);

        addRow.setOnClickListener(v -> {
            if (!isAccessibilityEnabled()) {
                Toast.makeText(activity, "Cần cấp quyền Trợ năng", Toast.LENGTH_SHORT).show();
                try {
                    activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                } catch (Exception ignored) {}
                return;
            }
            openAppPickerSubPanel();
        });

        containerKeyItems.addView(addRow);
    }

    public boolean cancelListeningMode() {
        if (listeningActionId != null) {
            remapManager.stopListeningMode();
            if (listeningBadgeView != null && previousBadgeText != null) {
                listeningBadgeView.setText(previousBadgeText);
                listeningBadgeView.setTextColor(previousBadgeColor);
            }
            listeningActionId = null;
            listeningBadgeView = null;
            previousBadgeText = null;
            return true;
        }
        return false;
    }

    public boolean handleBackIfListening(KeyEvent event) {
        if (listeningActionId != null) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK || event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    cancelListeningMode();
                }
                return true;
            }
        }
        return false;
    }

    private void startInlineListening(KeyRemapManager.ActionDef actionDef, String payload, String label, TextView badge, View row) {
        if (listeningActionId != null && listeningActionId.equals(actionDef.id)) {
            cancelListeningMode();
            return;
        }
        cancelListeningMode();

        listeningActionId = actionDef.id;
        listeningBadgeView = badge;
        if (badge != null) {
            previousBadgeText = badge.getText().toString();
            previousBadgeColor = badge.getCurrentTextColor();
            badge.setText("Bấm phím...");
            badge.setTextColor(row != null && row.hasFocus() ? Color.parseColor("#059669") : Color.parseColor("#10B981"));
        }

        remapManager.startListeningMode((scanCode, keyCodeCaptured, keyName) -> {
            KeyRemapManager.ActionDef conflict = remapManager.findConflictingAction(scanCode, keyCodeCaptured);
            if (conflict != null && !conflict.id.equals(actionDef.id)) {
                showOverwriteConfirmDialog(keyName, conflict.title, () -> {
                    remapManager.unassignAction(conflict.id);
                    remapManager.assignKeyToAction(actionDef.id, scanCode, keyCodeCaptured, keyName, payload, label);
                    populateActionItems();
                    Toast.makeText(activity, "Đã gán [" + keyName + "]", Toast.LENGTH_SHORT).show();
                    finishListening(badge, row, keyName);
                }, () -> {
                    cancelListeningMode();
                });
            } else {
                remapManager.assignKeyToAction(actionDef.id, scanCode, keyCodeCaptured, keyName, payload, label);
                populateActionItems();
                Toast.makeText(activity, "Đã gán [" + keyName + "]", Toast.LENGTH_SHORT).show();
                finishListening(badge, row, keyName);
            }
        });
    }

    private void finishListening(TextView badge, View row, String keyName) {
        if (badge != null) {
            badge.setText("[" + keyName + "]");
            badge.setTextColor(row != null && row.hasFocus() ? Color.parseColor("#059669") : Color.parseColor("#10B981"));
        }
        listeningActionId = null;
        listeningBadgeView = null;
        previousBadgeText = null;
    }

    private void showOverwriteConfirmDialog(String keyName, String conflictTitle, Runnable onConfirm, Runnable onCancel) {
        Dialog dialog = new Dialog(activity, R.style.Theme_TvHome);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.8f);
        }

        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.glass_panel);
        int pad = (int) (20 * density);
        root.setPadding(pad, pad, pad, pad);
        root.setLayoutParams(new ViewGroup.LayoutParams((int) (380 * density), ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView tvTitle = new TextView(activity);
        tvTitle.setText("Thay thế gán phím");
        tvTitle.setTextColor(Color.parseColor("#F4F4F5"));
        tvTitle.setTextSize(16f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        root.addView(tvTitle);

        TextView tvMsg = new TextView(activity);
        tvMsg.setText("Phím [" + keyName + "] đang được gán cho [" + conflictTitle + "].\nBạn có muốn thay thế không?");
        tvMsg.setTextColor(Color.parseColor("#A1A1AA"));
        tvMsg.setTextSize(13f);
        tvMsg.setPadding(0, (int) (8 * density), 0, (int) (16 * density));
        root.addView(tvMsg);

        LinearLayout btnRow = new LinearLayout(activity);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.END);

        Button btnCancel = new Button(activity);
        btnCancel.setText("Hủy");
        btnCancel.setTextColor(Color.parseColor("#F4F4F5"));
        btnCancel.setBackgroundResource(R.drawable.drawer_menu_item_bg);
        btnCancel.setFocusable(true);
        LinearLayout.LayoutParams lpCancel = new LinearLayout.LayoutParams(
                (int) (100 * density), (int) (40 * density));
        lpCancel.rightMargin = (int) (10 * density);
        btnCancel.setLayoutParams(lpCancel);
        btnCancel.setOnClickListener(v -> {
            dialog.dismiss();
            if (onCancel != null) onCancel.run();
        });
        btnCancel.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate().scaleX(hasFocus ? 1.05f : 1.0f).scaleY(hasFocus ? 1.05f : 1.0f).setDuration(120).start();
            btnCancel.setTextColor(Color.parseColor(hasFocus ? "#09090B" : "#F4F4F5"));
        });
        btnRow.addView(btnCancel);

        Button btnReplace = new Button(activity);
        btnReplace.setText("Thay thế");
        btnReplace.setTextColor(Color.parseColor("#09090B"));
        btnReplace.setBackgroundResource(R.drawable.search_pill_btn_bg);
        btnReplace.setFocusable(true);
        LinearLayout.LayoutParams lpReplace = new LinearLayout.LayoutParams(
                (int) (110 * density), (int) (40 * density));
        btnReplace.setLayoutParams(lpReplace);
        btnReplace.setOnClickListener(v -> {
            dialog.dismiss();
            if (onConfirm != null) onConfirm.run();
        });
        btnReplace.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate().scaleX(hasFocus ? 1.05f : 1.0f).scaleY(hasFocus ? 1.05f : 1.0f).setDuration(120).start();
        });
        btnRow.addView(btnReplace);

        root.addView(btnRow);
        dialog.setContentView(root);
        dialog.setOnCancelListener(d -> {
            if (onCancel != null) onCancel.run();
        });
        dialog.show();
        btnReplace.requestFocus();
    }

    private void openAppPickerSubPanel() {
        if (remoteSubPanel == null || containerRemoteSubPanelItems == null) return;
        isSubPanelOpen = true;

        if (cardWebRemoteQrFloat != null && cardWebRemoteQrFloat.getVisibility() == View.VISIBLE) {
            cardWebRemoteQrFloat.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(150)
                    .withEndAction(() -> cardWebRemoteQrFloat.setVisibility(View.GONE)).start();
        }

        containerRemoteSubPanelItems.removeAllViews();
        PackageManager pm = activity.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> rawApps = pm.queryIntentActivities(intent, 0);
        if (rawApps == null || rawApps.isEmpty()) {
            Intent fallback = new Intent(Intent.ACTION_MAIN, null);
            fallback.addCategory(Intent.CATEGORY_LAUNCHER);
            rawApps = pm.queryIntentActivities(fallback, 0);
        }

        if (rawApps == null || rawApps.isEmpty()) {
            Toast.makeText(activity, "Không tìm thấy ứng dụng", Toast.LENGTH_SHORT).show();
            return;
        }

        String myPkg = activity.getPackageName();
        Map<String, ResolveInfo> appMap = new LinkedHashMap<>();
        for (ResolveInfo ri : rawApps) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(myPkg)) continue;
            if (!appMap.containsKey(pkg)) {
                appMap.put(pkg, ri);
            }
        }
        List<ResolveInfo> apps = new ArrayList<>(appMap.values());

        Collections.sort(apps, (a, b) -> {
            String pkgA = a.activityInfo.packageName;
            String pkgB = b.activityInfo.packageName;
            KeyRemapManager.KeyBinding kbA = remapManager.getKeyBindingForAction("act_app_" + pkgA);
            KeyRemapManager.KeyBinding kbB = remapManager.getKeyBindingForAction("act_app_" + pkgB);
            boolean mappedA = (kbA != null && (kbA.scanCode > 0 || kbA.keyCode > 0));
            boolean mappedB = (kbB != null && (kbB.scanCode > 0 || kbB.keyCode > 0));
            if (mappedA != mappedB) {
                return mappedA ? -1 : 1; // Prioritize mapped apps at the top
            }
            String nameA = a.loadLabel(pm).toString();
            String nameB = b.loadLabel(pm).toString();
            return nameA.compareToIgnoreCase(nameB);
        });

        LayoutInflater inflater = LayoutInflater.from(activity);
        for (ResolveInfo info : apps) {
            String appName = info.loadLabel(pm).toString();
            String pkg = info.activityInfo.packageName;
            String actId = "act_app_" + pkg;

            View row = inflater.inflate(R.layout.item_remap_key_row, containerRemoteSubPanelItems, false);
            ImageView iconView = row.findViewById(R.id.imgKeyGroupIcon);
            TextView tvTitle = row.findViewById(R.id.txtKeyTitle);
            TextView tvBadge = row.findViewById(R.id.txtKeyActionBadge);

            try {
                iconView.setImageDrawable(info.loadIcon(pm));
                iconView.setImageTintList(null);
                iconView.clearColorFilter();
            } catch (Exception ignored) {
                iconView.setImageResource(R.drawable.ic_apps);
            }

            tvTitle.setText(appName);

            KeyRemapManager.KeyBinding kb = remapManager.getKeyBindingForAction(actId);
            if (kb != null && (kb.scanCode > 0 || kb.keyCode > 0)) {
                tvBadge.setText("[" + kb.keyName + "]");
                tvBadge.setTextColor(Color.parseColor("#10B981"));
            } else {
                tvBadge.setText("Bấm để gán");
                tvBadge.setTextColor(Color.parseColor("#71717A"));
            }

            applyInvertedFocus(row, iconView, tvTitle, null, tvBadge, true);

            KeyRemapManager.ActionDef appDef = new KeyRemapManager.ActionDef(actId, KeyRemapManager.TYPE_APP, appName, R.drawable.ic_apps);

            row.setOnClickListener(v -> {
                if (!isAccessibilityEnabled()) {
                    Toast.makeText(activity, "Cần cấp quyền Trợ năng", Toast.LENGTH_SHORT).show();
                    try {
                        activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    } catch (Exception ignored) {}
                    return;
                }
                startInlineListening(appDef, pkg, appName, tvBadge, row);
            });

            row.setOnLongClickListener(v -> {
                KeyRemapManager.KeyBinding binding = remapManager.getKeyBindingForAction(actId);
                if (binding != null && (binding.scanCode > 0 || binding.keyCode > 0)) {
                    cancelListeningMode();
                    remapManager.unassignAction(actId);
                    tvBadge.setText("Bấm để gán");
                    tvBadge.setTextColor(row.hasFocus() ? Color.parseColor("#52525B") : Color.parseColor("#71717A"));
                    populateActionItems();
                    Toast.makeText(activity, "Đã xóa gán " + appName, Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            });

            row.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        if (listeningActionId != null) {
                            cancelListeningMode();
                            return true;
                        }
                        closeAppPickerSubPanel();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        if (listeningActionId != null) {
                            cancelListeningMode();
                        }
                        closeAppPickerSubPanel();
                        return true;
                    }
                }
                return false;
            });

            containerRemoteSubPanelItems.addView(row);
        }

        remoteSubPanel.setVisibility(View.VISIBLE);
        float density = activity.getResources().getDisplayMetrics().density;
        int shift = remoteSubPanel.getWidth() > 0 ? remoteSubPanel.getWidth() : (int) (380 * density);
        remoteSubPanel.setTranslationX(shift);
        remoteSubPanel.animate().translationX(0f).setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    if (containerRemoteSubPanelItems.getChildCount() > 0) {
                        containerRemoteSubPanelItems.getChildAt(0).requestFocus();
                    }
                }).start();
    }

    public void closeAppPickerSubPanel() {
        if (!isSubPanelOpen || remoteSubPanel == null) return;
        cancelListeningMode();
        isSubPanelOpen = false;
        float density = activity.getResources().getDisplayMetrics().density;
        int shift = remoteSubPanel.getWidth() > 0 ? remoteSubPanel.getWidth() : (int) (380 * density);
        remoteSubPanel.animate().translationX(shift).setDuration(180)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    remoteSubPanel.setVisibility(View.GONE);
                    if (webRemoteServer != null && webRemoteServer.isRunning()) {
                        updateWebRemoteUi(true);
                    }
                    populateActionItems();
                    View currentFocus = activity.getCurrentFocus();
                    boolean isFocusInMain = currentFocus != null && drawerContent != null && isDescendantOf(drawerContent, currentFocus);
                    if (!isFocusInMain && containerKeyItems != null && containerKeyItems.getChildCount() > 0) {
                        containerKeyItems.getChildAt(containerKeyItems.getChildCount() - 1).requestFocus();
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

    interface AppSelectedCallback {
        void onSelected(String pkg, String name);
    }
}
