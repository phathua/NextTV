package com.next.tv.launcher.remote;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import java.io.File;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.next.tv.launcher.R;
import com.next.tv.launcher.lock.PinLockDialog;

public final class RemoteFileDrawer {
    private final Activity activity;
    private final Runnable onStateChangedListener;
    private final RemoteFileServer fileServer;

    private View drawerView;
    private View drawerContent;
    private boolean isShowing = false;
    private View previousFocusView;

    // Floating Left QR Card
    private View cardFileQrFloat;
    private ImageView imgFileQrFloat;
    private TextView txtFileUrlFloat;
    private TextView txtUploadNoticeFloat;

    // Floating Storage Breakdown (Modern Donut Chart & No-card border layout)
    private View cardStorageBreakdownFloat;
    private com.next.tv.launcher.clean.StorageDonutChartView storageDonutChart;
    private TextView txtBreakdownApk;
    private TextView txtBreakdownLog;
    private TextView txtBreakdownThumb;
    private TextView txtBreakdownCorpse;

    // Right Sidebar controls
    private View headerPermStorage;
    private View rowPermStorage;
    private View headerProtection;
    private View rowFileServerToggle;
    private View rowPasswordToggle;
    private View rowChangePin;
    private View rowCleanStorage;

    private TextView txtBadgeStorage;
    private Switch switchFileServer;
    private Switch switchPassword;
    private TextView txtPinStatus;
    private TextView txtBadgeChangePin;

    public RemoteFileDrawer(Activity activity, Runnable onStateChanged) {
        this.activity = activity;
        this.onStateChangedListener = onStateChanged;
        this.fileServer = RemoteFileServer.getInstance(activity);
    }

    public boolean isShowing() {
        return isShowing;
    }

    public void show() {
        show(null);
    }

    public void show(View triggerView) {
        if (isShowing && drawerView != null && drawerView.getParent() != null) {
            View firstFocus = drawerView.findViewById(R.id.rowFileServerToggle);
            if (firstFocus != null) firstFocus.requestFocus();
            return;
        }
        previousFocusView = triggerView != null ? triggerView : activity.getCurrentFocus();
        isShowing = false;

        LayoutInflater inflater = LayoutInflater.from(activity);
        drawerView = inflater.inflate(R.layout.drawer_remote_file, null);
        drawerContent = drawerView.findViewById(R.id.remoteFileContent);

        bindViews(drawerView);

        if (activity instanceof com.next.tv.launcher.MainActivity) {
            ((com.next.tv.launcher.MainActivity) activity).blockMainContentFocus();
        }

        activity.addContentView(drawerView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        isShowing = true;
        updateStorageBadge();

        if (drawerContent != null) {
            drawerContent.setTranslationX(400f);
            drawerContent.animate().translationX(0f).setDuration(220).start();
        }

        drawerView.postDelayed(() -> {
            if (isShowing && drawerView != null) {
                View firstFocus = null;
                if (!hasStoragePermission() && rowPermStorage != null && rowPermStorage.getVisibility() == View.VISIBLE) {
                    firstFocus = rowPermStorage;
                } else if (rowCleanStorage != null) {
                    firstFocus = rowCleanStorage;
                } else if (rowFileServerToggle != null) {
                    firstFocus = rowFileServerToggle;
                }
                if (firstFocus != null) {
                    firstFocus.requestFocus();
                }
            }
        }, 100);

        View.OnKeyListener dismissOnBackOrLeft = (v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    dismiss();
                    return true;
                }
            }
            return false;
        };

        View rowPermStorage = drawerView.findViewById(R.id.rowPermStorage);
        View rowCleanStorage = drawerView.findViewById(R.id.rowCleanStorage);
        View rowFileServerToggle = drawerView.findViewById(R.id.rowFileServerToggle);
        View rowPasswordToggle = drawerView.findViewById(R.id.rowPasswordToggle);
        View rowChangePin = drawerView.findViewById(R.id.rowChangePin);

        if (rowPermStorage != null) rowPermStorage.setOnKeyListener(dismissOnBackOrLeft);
        if (rowCleanStorage != null) rowCleanStorage.setOnKeyListener(dismissOnBackOrLeft);
        if (rowFileServerToggle != null) rowFileServerToggle.setOnKeyListener(dismissOnBackOrLeft);
        if (rowPasswordToggle != null) rowPasswordToggle.setOnKeyListener(dismissOnBackOrLeft);
        if (rowChangePin != null) rowChangePin.setOnKeyListener(dismissOnBackOrLeft);

        View root = drawerView.findViewById(R.id.remoteFileRoot);
        if (root != null) {
            root.setOnClickListener(v -> dismiss());
        }
    }

    public void dismiss() {
        if (!isShowing || drawerView == null) return;
        isShowing = false;
        if (activity instanceof com.next.tv.launcher.MainActivity) {
            ((com.next.tv.launcher.MainActivity) activity).unblockMainContentFocus();
        }

        if (activity instanceof com.next.tv.launcher.MainActivity) {
            ((com.next.tv.launcher.MainActivity) activity).focusUtilityItem(5);
        } else if (previousFocusView != null && previousFocusView.isShown()) {
            previousFocusView.post(() -> previousFocusView.requestFocus());
        }

        if (cardFileQrFloat != null && cardFileQrFloat.getVisibility() == View.VISIBLE) {
            cardFileQrFloat.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(160).start();
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
        // Floating QR views
        cardFileQrFloat = root.findViewById(R.id.cardFileQrFloat);
        imgFileQrFloat = root.findViewById(R.id.imgFileQrFloat);
        txtFileUrlFloat = root.findViewById(R.id.txtFileUrlFloat);
        txtUploadNoticeFloat = root.findViewById(R.id.txtUploadNoticeFloat);

        // Floating Breakdown with Donut Chart
        cardStorageBreakdownFloat = root.findViewById(R.id.cardStorageBreakdownFloat);
        storageDonutChart = root.findViewById(R.id.storageDonutChart);
        txtBreakdownApk = root.findViewById(R.id.txtBreakdownApk);
        txtBreakdownLog = root.findViewById(R.id.txtBreakdownLog);
        txtBreakdownThumb = root.findViewById(R.id.txtBreakdownThumb);
        txtBreakdownCorpse = root.findViewById(R.id.txtBreakdownCorpse);

        // Trigger pre-scan to show real cleanable MB
        triggerPreScan(root);

        // Sidebar rows
        headerPermStorage = root.findViewById(R.id.headerPermStorage);
        headerProtection = root.findViewById(R.id.headerProtection);
        rowFileServerToggle = root.findViewById(R.id.rowFileServerToggle);
        rowPasswordToggle = root.findViewById(R.id.rowPasswordToggle);
        rowChangePin = root.findViewById(R.id.rowChangePin);
        rowCleanStorage = root.findViewById(R.id.rowCleanStorage);
        rowPermStorage = root.findViewById(R.id.rowPermStorage);

        TextView txtCleanSubtitle = root.findViewById(R.id.txtCleanSubtitle);
        TextView txtBadgeClean = root.findViewById(R.id.txtBadgeClean);

        txtBadgeStorage = root.findViewById(R.id.txtBadgeStorage);
        updateStorageBadge();

        rowPermStorage.setOnClickListener(v -> requestStoragePermission());

        rowCleanStorage.setOnClickListener(v -> {
            if (!hasStoragePermission()) {
                Toast.makeText(activity, "Cần cấp quyền truy cập tệp", Toast.LENGTH_SHORT).show();
                requestStoragePermission();
                return;
            }
            if (txtBadgeClean != null) txtBadgeClean.setText("Đang dọn...");
            new com.next.tv.launcher.clean.TvStorageCleaner(activity).startClean(new com.next.tv.launcher.clean.TvStorageCleaner.CleanProgressListener() {
                @Override
                public void onProgress(String currentPhase, int percent) {
                    activity.runOnUiThread(() -> {
                        if (txtCleanSubtitle != null) {
                            txtCleanSubtitle.setText(currentPhase + " (" + percent + "%)");
                        }
                    });
                }

                @Override
                public void onComplete(com.next.tv.launcher.clean.TvStorageCleaner.CleanResult result) {
                    activity.runOnUiThread(() -> {
                        if (txtCleanSubtitle != null) {
                            txtCleanSubtitle.setText("Đã xóa " + result.deletedFilesCount + " tệp rác");
                        }
                        if (txtBadgeClean != null) {
                            String freed = result.junkBytesFreed > 0 ? "+" + com.next.tv.launcher.clean.TvStorageCleaner.formatSize(result.junkBytesFreed) : "Tối ưu";
                            txtBadgeClean.setText(freed);
                        }
                        Toast.makeText(activity, "Đã dọn sạch: " + com.next.tv.launcher.clean.TvStorageCleaner.formatSize(result.junkBytesFreed), Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });

        // File server toggle
        switchFileServer = root.findViewById(R.id.switchFileServer);

        boolean isRunning = fileServer.isRunning();
        switchFileServer.setChecked(isRunning);
        updateFileServerUi(isRunning);

        rowFileServerToggle.setOnClickListener(v -> {
            boolean next = !switchFileServer.isChecked();
            if (next && !hasStoragePermission()) {
                Toast.makeText(activity, "Cần cấp quyền truy cập tệp", Toast.LENGTH_SHORT).show();
                requestStoragePermission();
                return;
            }
            switchFileServer.setChecked(next);
            if (next) {
                fileServer.start();
            } else {
                fileServer.stop();
            }
            updateFileServerUi(next);
            if (onStateChangedListener != null) onStateChangedListener.run();
        });

        fileServer.setOnFileUploadedListener(file -> {
            if (file != null) {
                String name = file.getName();
                activity.runOnUiThread(() -> {
                    if (txtUploadNoticeFloat != null) {
                        txtUploadNoticeFloat.setVisibility(View.VISIBLE);
                        txtUploadNoticeFloat.setText("Đã nhận: " + name);
                    }
                    Toast.makeText(activity, "Đã nhận tệp: " + name, Toast.LENGTH_SHORT).show();
                });
            }
        });

        // Password protection
        switchPassword = root.findViewById(R.id.switchPassword);
        txtPinStatus = root.findViewById(R.id.txtPinStatus);
        txtBadgeChangePin = root.findViewById(R.id.txtBadgeChangePin);

        boolean isAuth = fileServer.isAuthEnabled();
        switchPassword.setChecked(isAuth);
        updatePinUi();

        rowPasswordToggle.setOnClickListener(v -> {
            boolean next = !switchPassword.isChecked();
            if (next && fileServer.getPassword().isEmpty()) {
                openPinSetupDialog(() -> {
                    switchPassword.setChecked(true);
                    fileServer.setAuthEnabled(true);
                    updatePinUi();
                });
                return;
            }
            switchPassword.setChecked(next);
            fileServer.setAuthEnabled(next);
            updatePinUi();
        });

        rowChangePin.setOnClickListener(v -> openPinSetupDialog(this::updatePinUi));

        // Apple tvOS Inverted Focus
        applyInvertedFocus(rowFileServerToggle,
                rowFileServerToggle.findViewWithTag(null) instanceof ImageView ? (ImageView) rowFileServerToggle.findViewWithTag(null) : getFirstImageView(rowFileServerToggle),
                getFirstTextView(rowFileServerToggle), null, null);

        applyInvertedFocus(rowPasswordToggle,
                getFirstImageView(rowPasswordToggle),
                getFirstTextView(rowPasswordToggle), null, null);

        applyInvertedFocus(rowChangePin,
                getFirstImageView(rowChangePin),
                getFirstTextView(rowChangePin),
                txtPinStatus,
                txtBadgeChangePin);

        rowCleanStorage.setOnFocusChangeListener((v, hasFocus) -> {
            showBreakdownCard(hasFocus);
        });

        applyInvertedFocus(rowCleanStorage,
                getFirstImageView(rowCleanStorage),
                getFirstTextView(rowCleanStorage),
                txtCleanSubtitle,
                txtBadgeClean);

        applyInvertedFocus(rowPermStorage,
                getFirstImageView(rowPermStorage),
                getFirstTextView(rowPermStorage),
                null,
                txtBadgeStorage);
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
        if (row == null) return;
        View.OnFocusChangeListener existingListener = row.getOnFocusChangeListener();
        row.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate().scaleX(hasFocus ? 1.01f : 1.0f).scaleY(hasFocus ? 1.02f : 1.0f).setDuration(120).start();
            if (icon != null) {
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
                    badge.setTextColor(Color.parseColor("#09090B"));
                } else {
                    if (badge == txtBadgeStorage) {
                        updateStorageBadge();
                    } else if (badge == txtBadgeChangePin) {
                        badge.setTextColor(Color.parseColor("#10B981"));
                    } else if (badge.getId() == R.id.txtBadgeClean) {
                        badge.setTextColor(Color.parseColor("#10B981"));
                    }
                }
            }
            if (existingListener != null) {
                existingListener.onFocusChange(v, hasFocus);
            }
        });
    }

    private void triggerPreScan(View root) {
        TextView txtBadgeClean = root.findViewById(R.id.txtBadgeClean);
        TextView txtCleanSubtitle = root.findViewById(R.id.txtCleanSubtitle);
        if (!hasStoragePermission()) {
            if (txtBadgeClean != null) txtBadgeClean.setText("Cấp quyền");
            return;
        }

        new com.next.tv.launcher.clean.TvStorageCleaner(activity).startScan(breakdown -> {
            activity.runOnUiThread(() -> {
                long total = breakdown.getTotalCleanableBytes();
                if (txtBadgeClean != null) {
                    if (total > 0) {
                        txtBadgeClean.setText(com.next.tv.launcher.clean.TvStorageCleaner.formatSize(total));
                        txtBadgeClean.setTextColor(Color.parseColor("#10B981"));
                    } else {
                        txtBadgeClean.setText("Tối ưu");
                        txtBadgeClean.setTextColor(Color.parseColor("#10B981"));
                    }
                }
                if (txtCleanSubtitle != null) {
                    if (total > 0) {
                        txtCleanSubtitle.setText("Phát hiện " + (breakdown.apkCount + breakdown.logTmpCount + breakdown.thumbCount + breakdown.corpseCount) + " mục rác");
                    } else {
                        txtCleanSubtitle.setText("Bộ nhớ sạch sẽ");
                    }
                }
                updateBreakdownCard(breakdown);
            });
        });
    }

    private void updateBreakdownCard(com.next.tv.launcher.clean.TvStorageCleaner.ScanBreakdown b) {
        File root = Environment.getExternalStorageDirectory();
        long free = root != null ? root.getFreeSpace() : 0;
        long total = root != null ? root.getTotalSpace() : 0;

        if (storageDonutChart != null) {
            storageDonutChart.updateData(b, free, total);
        }
        if (txtBreakdownApk != null) {
            txtBreakdownApk.setText(com.next.tv.launcher.clean.TvStorageCleaner.formatSize(b.apkBytes) + " (" + b.apkCount + " tệp)");
        }
        if (txtBreakdownLog != null) {
            txtBreakdownLog.setText(com.next.tv.launcher.clean.TvStorageCleaner.formatSize(b.logTmpBytes) + " (" + b.logTmpCount + " tệp)");
        }
        if (txtBreakdownThumb != null) {
            txtBreakdownThumb.setText(com.next.tv.launcher.clean.TvStorageCleaner.formatSize(b.thumbBytes) + " (" + b.thumbCount + " mục)");
        }
        if (txtBreakdownCorpse != null) {
            txtBreakdownCorpse.setText(com.next.tv.launcher.clean.TvStorageCleaner.formatSize(b.corpseBytes) + " (" + b.corpseCount + " thư mục)");
        }
    }

    private void showBreakdownCard(boolean show) {
        if (cardStorageBreakdownFloat == null) return;
        if (show) {
            // Hide QR card if visible
            if (cardFileQrFloat != null && cardFileQrFloat.getVisibility() == View.VISIBLE) {
                cardFileQrFloat.setVisibility(View.GONE);
            }
            if (cardStorageBreakdownFloat.getVisibility() != View.VISIBLE) {
                cardStorageBreakdownFloat.setVisibility(View.VISIBLE);
                cardStorageBreakdownFloat.setAlpha(0f);
                cardStorageBreakdownFloat.setScaleX(0.92f);
                cardStorageBreakdownFloat.setScaleY(0.92f);
                cardStorageBreakdownFloat.animate().alpha(1f).scaleX(1.0f).scaleY(1.0f).setDuration(220).start();
            }
        } else {
            if (cardStorageBreakdownFloat.getVisibility() == View.VISIBLE) {
                cardStorageBreakdownFloat.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(160)
                        .withEndAction(() -> {
                            if (cardStorageBreakdownFloat != null) cardStorageBreakdownFloat.setVisibility(View.GONE);
                            // Restore QR if server running
                            if (fileServer != null && fileServer.isRunning() && cardFileQrFloat != null) {
                                cardFileQrFloat.setVisibility(View.VISIBLE);
                                cardFileQrFloat.setAlpha(1f);
                                cardFileQrFloat.setScaleX(1f);
                                cardFileQrFloat.setScaleY(1f);
                            }
                        }).start();
            }
        }
    }

    private void updateStorageBadge() {
        boolean hasPerm = hasStoragePermission();
        if (headerPermStorage != null) {
            headerPermStorage.setVisibility(hasPerm ? View.GONE : View.VISIBLE);
        }
        if (rowPermStorage != null) {
            rowPermStorage.setVisibility(hasPerm ? View.GONE : View.VISIBLE);
        }
        if (txtBadgeStorage == null) return;
        if (hasPerm) {
            txtBadgeStorage.setText("Đã cấp");
            txtBadgeStorage.setTextColor(Color.parseColor("#10B981"));
        } else {
            txtBadgeStorage.setText("Cấp quyền");
            txtBadgeStorage.setTextColor(Color.parseColor("#FFB340"));
        }
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestStoragePermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(intent);
            } else {
                activity.requestPermissions(new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, 101);
            }
        } catch (Exception e) {
            try {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(intent);
            } catch (Exception ignored) {}
        }
    }

    private void updateFileServerUi(boolean running) {
        if (headerProtection != null) {
            headerProtection.setAlpha(running ? 1.0f : 0.4f);
        }
        if (rowPasswordToggle != null) {
            rowPasswordToggle.setAlpha(running ? 1.0f : 0.4f);
            rowPasswordToggle.setFocusable(running);
            rowPasswordToggle.setClickable(running);
        }
        if (rowChangePin != null) {
            rowChangePin.setAlpha(running ? 1.0f : 0.4f);
            rowChangePin.setFocusable(running);
            rowChangePin.setClickable(running);
        }

        if (cardFileQrFloat == null) return;
        if (running) {
            String ip = RemoteFileServer.getIpAddress(activity);
            String url = "http://" + ip + ":" + fileServer.getPort();
            txtFileUrlFloat.setText(url);
            Bitmap qr = QrCodeMatrixGenerator.generateQrBitmap(url, 440);
            if (qr != null) {
                imgFileQrFloat.setImageBitmap(qr);
            }
            if (cardFileQrFloat.getVisibility() != View.VISIBLE) {
                cardFileQrFloat.setVisibility(View.VISIBLE);
                cardFileQrFloat.setAlpha(0f);
                cardFileQrFloat.setScaleX(0.92f);
                cardFileQrFloat.setScaleY(0.92f);
                cardFileQrFloat.animate().alpha(1f).scaleX(1.0f).scaleY(1.0f).setDuration(220).start();
            }
        } else {
            if (cardFileQrFloat.getVisibility() == View.VISIBLE) {
                cardFileQrFloat.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(160)
                        .withEndAction(() -> cardFileQrFloat.setVisibility(View.GONE)).start();
            }
        }
    }

    private void updatePinUi() {
        if (txtPinStatus == null) return;
        String pass = fileServer.getPassword();
        if (pass.isEmpty()) {
            txtPinStatus.setText("Chưa đặt mã");
            txtBadgeChangePin.setText("Thiết lập");
        } else {
            txtPinStatus.setText("Mã PIN 4 số");
            txtBadgeChangePin.setText("Thay đổi");
        }
    }

    private void openPinSetupDialog(Runnable onSuccess) {
        new PinLockDialog(activity, PinLockDialog.MODE_CREATE, "Đặt mã PIN bảo vệ", "Mật khẩu quản lý tệp", new PinLockDialog.PinValueListener() {
            @Override
            public void onPinEntered(String pin) {
                if (pin != null && !pin.isEmpty()) {
                    fileServer.setPassword(pin);
                }
                if (onSuccess != null) onSuccess.run();
                Toast.makeText(activity, "Đã lưu mã bảo vệ", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCancel() {}
        }).show();
    }
}
